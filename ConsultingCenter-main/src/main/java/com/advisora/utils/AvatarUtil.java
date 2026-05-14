package com.advisora.utils;

import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

public class AvatarUtil {

    public static void makeCircular(ImageView iv) {
        if (iv == null) return;

        Circle clip = new Circle();

        // Center + radius based on fitWidth/fitHeight
        clip.centerXProperty().bind(iv.fitWidthProperty().divide(2));
        clip.centerYProperty().bind(iv.fitHeightProperty().divide(2));

        // Use the smallest to avoid oval if width != height
        clip.radiusProperty().bind(
                iv.fitWidthProperty().divide(2)
        );

        iv.setClip(clip);
        iv.setSmooth(true);
        iv.setPreserveRatio(true);
    }
}

