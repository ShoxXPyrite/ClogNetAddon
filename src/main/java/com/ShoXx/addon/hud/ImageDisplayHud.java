package com.ShoXx.addon.hud;

import com.ShoXx.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Iterator;

public class ImageDisplayHud extends HudElement {
    public static final HudElementInfo<ImageDisplayHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP,
        "image-display",
        "Display images and animated GIFs on your HUD.",
        ImageDisplayHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> imagePath = sgGeneral.add(new StringSetting.Builder()
        .name("image-path")
        .description("Path to the image or GIF file.")
        .defaultValue("")
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the image.")
        .defaultValue(1.0)
        .min(0.1)
        .max(5.0)
        .sliderMin(0.1)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Integer> opacity = sgGeneral.add(new IntSetting.Builder()
        .name("opacity")
        .description("Opacity of the image (0-255).")
        .defaultValue(255)
        .min(0)
        .max(255)
        .sliderMin(0)
        .sliderMax(255)
        .build()
    );

    private final Setting<Boolean> showBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("show-background")
        .description("Show a background behind the image.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Background color.")
        .defaultValue(new SettingColor(0, 0, 0, 100))
        .visible(showBackground::get)
        .build()
    );

    // Image handling variables
    private Identifier textureId;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private String lastImagePath = "";

    // GIF handling variables
    private BufferedImage[] gifFrames;
    private int currentFrame = 0;
    private long lastFrameTime = 0;
    private boolean isGif = false;

    // Reflection for setColor method
    private static Method setColorMethod;

    static {
        try {
            setColorMethod = NativeImage.class.getDeclaredMethod("setColor", int.class, int.class, int.class);
            setColorMethod.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ImageDisplayHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        // Check if image path has changed
        if (!imagePath.get().equals(lastImagePath)) {
            loadImage();
            lastImagePath = imagePath.get();
        }

        // If no valid image is loaded, show placeholder
        if (textureId == null || imageWidth == 0 || imageHeight == 0) {
            renderPlaceholder(renderer);
            return;
        }

        // Update GIF frame if needed
        if (isGif && gifFrames != null && gifFrames.length > 1) {
            updateGifFrame();
        }

        // Calculate scaled dimensions
        double scaleFactor = scale.get();
        int scaledWidth = (int) (imageWidth * scaleFactor);
        int scaledHeight = (int) (imageHeight * scaleFactor);

        // Set HUD element size
        setSize(scaledWidth, scaledHeight);

        // Render background if enabled
        if (showBackground.get()) {
            renderer.quad(x, y, scaledWidth, scaledHeight, backgroundColor.get());
        }

        // Render the image
        Color imageColor = new Color(255, 255, 255, opacity.get());
        renderer.texture(textureId, x, y, scaledWidth, scaledHeight, imageColor);
    }

    private void renderPlaceholder(HudRenderer renderer) {
        String text = imagePath.get().isEmpty() ? "No image path set" : "Failed to load image";
        int textWidth = (int) renderer.textWidth(text, false);
        int textHeight = (int) renderer.textHeight(false);

        setSize(textWidth + 10, textHeight + 10);

        // Background
        renderer.quad(x, y, getWidth(), getHeight(), new Color(50, 50, 50, 150));

        // Text
        renderer.text(text, x + 5, y + 5, Color.WHITE, false);
    }

    private void loadImage() {
        try {
            // Clear previous texture
            if (textureId != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
                textureId = null;
            }

            String path = imagePath.get().trim();
            if (path.isEmpty()) {
                return;
            }

            File imageFile = new File(path);
            if (!imageFile.exists()) {
                return;
            }

            // Check if it's a GIF
            if (path.toLowerCase().endsWith(".gif")) {
                loadGif(imageFile);
            } else {
                loadStaticImage(imageFile);
            }

        } catch (Exception e) {
            e.printStackTrace();
            // Reset on error
            textureId = null;
            imageWidth = 0;
            imageHeight = 0;
            isGif = false;
            gifFrames = null;
        }
    }

    private void loadStaticImage(File imageFile) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(imageFile);
        if (bufferedImage == null) {
            return;
        }

        imageWidth = bufferedImage.getWidth();
        imageHeight = bufferedImage.getHeight();
        isGif = false;

        // Convert BufferedImage to NativeImage
        NativeImage nativeImage = new NativeImage(imageWidth, imageHeight, false);
        for (int x = 0; x < imageWidth; x++) {
            for (int y = 0; y < imageHeight; y++) {
                int argb = bufferedImage.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                try {
                    setColorMethod.invoke(nativeImage, x, y, abgr);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Create texture using correct constructor
        textureId = Identifier.of("TNTaddon", "image_display_hud_" + System.currentTimeMillis());
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> textureId.toString(), nativeImage);
        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
    }

    private void loadGif(File gifFile) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new FileInputStream(gifFile))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return;
            }

            ImageReader reader = readers.next();
            reader.setInput(iis);

            int frameCount = reader.getNumImages(true);
            gifFrames = new BufferedImage[frameCount];

            // Load all frames
            for (int i = 0; i < frameCount; i++) {
                gifFrames[i] = reader.read(i);
            }

            if (gifFrames.length > 0) {
                imageWidth = gifFrames[0].getWidth();
                imageHeight = gifFrames[0].getHeight();
                isGif = true;
                currentFrame = 0;
                lastFrameTime = System.currentTimeMillis();

                // Load first frame as texture
                loadFrameAsTexture(gifFrames[0]);
            }

            reader.dispose();
        }
    }

    private void loadFrameAsTexture(BufferedImage frame) {
        try {
            // Clear previous texture
            if (textureId != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
            }

            // Convert BufferedImage to NativeImage
            NativeImage nativeImage = new NativeImage(imageWidth, imageHeight, false);
            for (int x = 0; x < imageWidth; x++) {
                for (int y = 0; y < imageHeight; y++) {
                    int argb = frame.getRGB(x, y);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                    try {
                        setColorMethod.invoke(nativeImage, x, y, abgr);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            // Create texture using correct constructor
            textureId = Identifier.of("shoxxaddon", "image_display_hud_frame_" + System.currentTimeMillis());
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> textureId.toString(), nativeImage);
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateGifFrame() {
        long currentTime = System.currentTimeMillis();
        // Default 100ms delay
        int frameDelay = 100;
        if (currentTime - lastFrameTime >= frameDelay) {
            currentFrame = (currentFrame + 1) % gifFrames.length;
            loadFrameAsTexture(gifFrames[currentFrame]);
            lastFrameTime = currentTime;
        }
    }
}
