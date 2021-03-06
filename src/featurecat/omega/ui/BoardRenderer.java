package featurecat.omega.ui;

import featurecat.omega.rules.Zobrist;
import featurecat.omega.Omega;
import featurecat.omega.rules.Board;
import featurecat.omega.rules.Stone;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BoardRenderer {
    private static final double MARGIN = 0.03; // percentage of the boardLength to offset before drawing black lines
    private static final double STARPOINT_DIAMETER = 0.015;

    private int x, y;
    private int boardLength;

    private int scaledMargin, availableLength, squareLength, stoneRadius;

    private BufferedImage cachedBackgroundImage = null;

    private BufferedImage cachedStonesImage = null;
    private BufferedImage cachedStonesShadowImage = null;
    private Zobrist cachedZhash = new Zobrist(); // defaults to an empty board

    private BufferedImage branchStonesImage = null;
    private BufferedImage branchStonesShadowImage = null;


    /**
     * Draw a go board
     */
    public void draw(Graphics2D g) {
        if (Omega.frame == null || Omega.board == null)
            return;

        setupSizeParameters();

        drawBackground(g);

        drawHeatmap(g);
        drawIntersections(g);

        if (Omega.showStones) {
            drawStones();

            renderImages(g);
            // actually, if you end the if statement here, Influencie could be a nice blind-go client :). or as-is too is good.
            drawMoveNumbers(g);
        }
    }

    /**
     * Calculate good values for boardLength, scaledMargin, availableLength, and squareLength
     */
    private void setupSizeParameters() {
        int[] calculatedPixelMargins = calculatePixelMargins();
        boardLength = calculatedPixelMargins[0];
        scaledMargin = calculatedPixelMargins[1];
        availableLength = calculatedPixelMargins[2];

        squareLength = calculateSquareLength(availableLength);
        stoneRadius = squareLength / 2 - 1;
    }

    public static int intersectionColor = 0;
    private int cachedIntersectionColor = intersectionColor;

    public static boolean blackStoneOutline = false;
    private boolean cachedBlackStoneOutline = blackStoneOutline;

    public static boolean showIntersectionsOverHeatmap = true;
    private boolean cachedShowIntersectionsOverHeatmap = showIntersectionsOverHeatmap;

    /**
     * Draw the green background and go board with lines. We cache the image for a performance boost.
     */
    private void drawBackground(Graphics2D g0) {
        // draw the cached background image if frame size changes
        if (cachedBackgroundImage == null || cachedBackgroundImage.getWidth() != Omega.frame.getWidth() ||
                cachedBackgroundImage.getHeight() != Omega.frame.getHeight() ||
                cachedIntersectionColor != intersectionColor || cachedBoardTypeIndex != boardTypeIndex ||
        cachedShowIntersectionsOverHeatmap != showIntersectionsOverHeatmap) {
            cachedShowIntersectionsOverHeatmap = showIntersectionsOverHeatmap;

            cachedBackgroundImage = new BufferedImage(Omega.frame.getWidth(), Omega.frame.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cachedBackgroundImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // draw the wooden background
            drawWoodenBoard(g);

            g.dispose();
        }

        //g0.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g0.drawImage(cachedBackgroundImage, 0, 0, null);
    }

    private void drawIntersections(Graphics2D g) {
        // draw the lines
        cachedIntersectionColor = intersectionColor;
        if (intersectionColor != 1) {
            g.setColor(intersectionColor == 2 ? Color.WHITE : Color.BLACK);
            for (int i = 0; i < Board.BOARD_SIZE; i++) {
                g.drawLine(x + scaledMargin, y + scaledMargin + squareLength * i,
                        x + scaledMargin + availableLength - 1, y + scaledMargin + squareLength * i);
            }
            for (int i = 0; i < Board.BOARD_SIZE; i++) {
                g.drawLine(x + scaledMargin + squareLength * i, y + scaledMargin,
                        x + scaledMargin + squareLength * i, y + scaledMargin + availableLength - 1);
            }

            // draw the star points
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int starPointRadius = (int) (STARPOINT_DIAMETER * boardLength) / 2;
            final int NUM_STARPOINTS = 3;
            final int STARPOINT_EDGE_OFFSET = 3;
            final int STARPOINT_GRID_DISTANCE = 6;
            for (int i = 0; i < NUM_STARPOINTS; i++) {
                for (int j = 0; j < NUM_STARPOINTS; j++) {
                    int centerX = x + scaledMargin + squareLength * (STARPOINT_EDGE_OFFSET + STARPOINT_GRID_DISTANCE * i);
                    int centerY = y + scaledMargin + squareLength * (STARPOINT_EDGE_OFFSET + STARPOINT_GRID_DISTANCE * j);
                    fillCircle(g, centerX, centerY, starPointRadius);
                }
            }
        }
    }

    /**
     * Draw the stones. We cache the image for a performance boost.
     */
    private void drawStones() {
        // draw a new image if frame size changes or board state changes
        if (cachedStonesImage == null || cachedStonesImage.getWidth() != boardLength ||
                cachedStonesImage.getHeight() != boardLength ||
                !cachedZhash.equals(Omega.board.getData().zobrist) ||
                cachedBlackStoneOutline != blackStoneOutline) {

            cachedBlackStoneOutline = blackStoneOutline;
            cachedStonesImage = new BufferedImage(boardLength, boardLength, BufferedImage.TYPE_INT_ARGB);
            cachedStonesShadowImage = new BufferedImage(boardLength, boardLength, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cachedStonesImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Graphics2D gShadow = cachedStonesShadowImage.createGraphics();
            gShadow.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // we need antialiasing to make the stones pretty. Java is a bit slow at antialiasing; that's why we want the cache
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gShadow.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            for (int i = 0; i < Board.BOARD_SIZE; i++) {
                for (int j = 0; j < Board.BOARD_SIZE; j++) {
                    int stoneX = scaledMargin + squareLength * i;
                    int stoneY = scaledMargin + squareLength * j;
                    drawStone(g, gShadow, stoneX, stoneY, Omega.board.getStones()[Board.getIndex(i, j)]);
                }
            }

            cachedZhash = Omega.board.getData().zobrist;
            g.dispose();
            gShadow.dispose();
        }
    }

    /**
     * render the shadows and stones in correct background-foreground order
     */
    private void renderImages(Graphics2D g) {
        //g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.drawImage(cachedStonesShadowImage, x, y, null);
        g.drawImage(branchStonesShadowImage, x, y, null);
        g.drawImage(cachedStonesImage, x, y, null);
        g.drawImage(branchStonesImage, x, y, null);
    }

    /**
     * Draw move numbers and/or mark the last played move
     */
    private void drawMoveNumbers(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int[] lastMove = Omega.board.getLastMove();
        if (lastMove != null) {
            // mark the last coordinate
            int lastMoveMarkerRadius = stoneRadius / 2;
            int stoneX = x + scaledMargin + squareLength * lastMove[0];
            int stoneY = y + scaledMargin + squareLength * lastMove[1];

            // set color to the opposite color of whatever is on the board
            g.setColor(Omega.board.getStones()[Board.getIndex(lastMove[0], lastMove[1])].isWhite() ?
                    Color.BLACK : Color.WHITE);
            drawCircle(g, stoneX, stoneY, lastMoveMarkerRadius);
        } else if (lastMove == null && Omega.board.getData().lastMoveColor != Stone.EMPTY && Omega.board.getData().moveNumber != 0) {
            g.setColor(Omega.board.getData().blackToPlay ? new Color(255, 255, 255, 150) : new Color(0, 0, 0, 150));
            g.fillOval(x + boardLength / 2 - 4 * stoneRadius, y + boardLength / 2 - 4 * stoneRadius, stoneRadius * 8, stoneRadius * 8);
            g.setColor(Omega.board.getData().blackToPlay ? new Color(0, 0, 0, 255) : new Color(255, 255, 255, 255));
            drawString(g, x + boardLength / 2, y + boardLength / 2, "Open Sans", "pass", stoneRadius * 4, stoneRadius * 6);
        }
    }

    public static boolean useGradient = true;
    public static int maxAlpha = 180;
    public static int whiteAuraRed = 0x00; // old 0x184bb5
    public static int whiteAuraGreen = 0x80;// new 0x00B2FF 6e
    public static int whiteAuraBlue = 0xFF; // 0080ff

    public static int blackAuraRed = 0xBF;
    public static int blackAuraGreen = 0x00; // old 0xf88a00 new 0xFFAE00 8c
    public static int blackAuraBlue = 0x4F; // ff8000

    /**
     * Draw all of Leelaz's suggestions as colored stones with winrate/playout statistics overlayed
     */
    private void drawHeatmap(Graphics2D g) {
        if (Omega.showHeatmap) {
            final double[] heatmap = Omega.board.getInfluenceHeatmap();
            for (int i = 0; i < Board.BOARD_SIZE; i++) {
                for (int j = 0; j < Board.BOARD_SIZE; j++) {

                    int suggestionX = x + scaledMargin + squareLength * i;
                    int suggestionY = y + scaledMargin + squareLength * j;
                    double heatValue = heatmap[Board.getIndex(i, j)];

                    double alphaConst = Math.min(255, useGradient ? maxAlpha : maxAlpha * 1.75);
                    int alpha = (int) Math.abs(alphaConst * (heatValue - 0.5) * 2);

                    Color hsbColor = heatValue > 0.5 ? new Color(blackAuraRed, blackAuraGreen, blackAuraBlue) : new Color(whiteAuraRed, whiteAuraGreen, whiteAuraBlue);
                    Color color = new Color(hsbColor.getRed(), hsbColor.getGreen(), hsbColor.getBlue(), alpha);

                    Paint original = g.getPaint();
                    g.setColor(color);
                    if (useGradient) {
                        Point2D center = new Point2D.Float(suggestionX, suggestionY);
                        float radius = squareLength * 2 + 2;
                        float[] dist = {0.0f, 0.5f + 0.5f * (float) Math.max(0, Math.log((float) alpha / alphaConst) / 0.8 + 1)}; // out of 0.7, 0.8, 0.9, 1.0, 1.1, I liked 0.8 best
                        Color[] colors = {color, new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)};
                        RadialGradientPaint p =
                                new RadialGradientPaint(center, radius, dist, colors);
                        g.setPaint(p);
                        int gradRadius = (int) radius;
                        g.fillRect(suggestionX - gradRadius, suggestionY - gradRadius, gradRadius * 2 + 2, gradRadius * 2 + 2);
                    } else {
                        g.fillRect(suggestionX - stoneRadius, suggestionY - stoneRadius, squareLength, squareLength);
                    }
                    g.setPaint(original);
                    g.setStroke(new BasicStroke(1));
                }
            }
        }
    }

    public static int boardTypeIndex = 1;
    private int cachedBoardTypeIndex = boardTypeIndex;

    private void drawWoodenBoard(Graphics2D g) {
        // fancy version
        try {
            int shadowRadius = (int) (boardLength * MARGIN / 6);
            cachedBoardTypeIndex = boardTypeIndex;
            g.drawImage(ImageIO.read(new File("assets/board" + boardTypeIndex + ".png")), x - 2 * shadowRadius, y - 2 * shadowRadius, boardLength + 4 * shadowRadius, boardLength + 4 * shadowRadius, null);
            g.setStroke(new BasicStroke(shadowRadius * 2));
//             draw border
//            g.setColor(new Color(0, 0, 0, 50));
//            g.drawRect(x - shadowRadius, y - shadowRadius, boardLength + 2 * shadowRadius, boardLength + 2 * shadowRadius);
            g.setStroke(new BasicStroke(1));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Calculates the lengths and pixel margins from a given boardLength.
     *
     * @param boardLength go board's length in pixels; must be boardLength >= BOARD_SIZE - 1
     * @return an array containing the three outputs: new boardLength, scaledMargin, availableLength
     */
    private static int[] calculatePixelMargins(int boardLength) {
        int scaledMargin;
        int availableLength;

        // decrease boardLength until the availableLength will result in square board intersections
        boardLength++;
        do {
            boardLength--;
            scaledMargin = (int) (MARGIN * boardLength);
            availableLength = boardLength - 2 * scaledMargin;
        }
        while (!((availableLength - 1) % (Board.BOARD_SIZE - 1) == 0));
        // this will be true if BOARD_SIZE - 1 square intersections, plus one line, will fit

        return new int[]{boardLength, scaledMargin, availableLength};
    }

    private void drawShadow(Graphics2D g, int centerX, int centerY, boolean isGhost) {
        drawShadow(g, centerX, centerY, isGhost, 1);
    }

    private static final int SHADOW_SIZE = 100; // TODO remove hardcoded value

    private void drawShadow(Graphics2D g, int centerX, int centerY, boolean isGhost, float shadowStrength) {
        final int shadowSize = (int) (stoneRadius * 0.3 * SHADOW_SIZE / 100);
        final int fartherShadowSize = (int) (stoneRadius * 0.17 * SHADOW_SIZE / 100);


        final Paint TOP_GRADIENT_PAINT;
        final Paint LOWER_RIGHT_GRADIENT_PAINT;

        if (isGhost) {
            TOP_GRADIENT_PAINT = new RadialGradientPaint(new Point2D.Float(centerX, centerY),
                    stoneRadius + shadowSize, new float[]{((float) stoneRadius / (stoneRadius + shadowSize)) - 0.0001f, ((float) stoneRadius / (stoneRadius + shadowSize)), 1.0f}, new Color[]{
                    new Color(0, 0, 0, 0), new Color(50, 50, 50, (int) (120 * shadowStrength)), new Color(0, 0, 0, 0)
            });

            LOWER_RIGHT_GRADIENT_PAINT = new RadialGradientPaint(new Point2D.Float(centerX + shadowSize * 2 / 3, centerY + shadowSize * 2 / 3),
                    stoneRadius + fartherShadowSize, new float[]{0.6f, 1.0f}, new Color[]{
                    new Color(0, 0, 0, 180), new Color(0, 0, 0, 0)
            });
        } else {
            TOP_GRADIENT_PAINT = new RadialGradientPaint(new Point2D.Float(centerX, centerY),
                    stoneRadius + shadowSize, new float[]{0.3f, 1.0f}, new Color[]{
                    new Color(50, 50, 50, 150), new Color(0, 0, 0, 0)
            });
            LOWER_RIGHT_GRADIENT_PAINT = new RadialGradientPaint(new Point2D.Float(centerX + shadowSize, centerY + shadowSize),
                    stoneRadius + fartherShadowSize, new float[]{0.6f, 1.0f}, new Color[]{
                    new Color(0, 0, 0, 140), new Color(0, 0, 0, 0)
            });
        }

        final Paint originalPaint = g.getPaint();

        g.setPaint(TOP_GRADIENT_PAINT);
        fillCircle(g, centerX, centerY, stoneRadius + shadowSize);
        if (!isGhost) {
            g.setPaint(LOWER_RIGHT_GRADIENT_PAINT);
            fillCircle(g, centerX + shadowSize, centerY + shadowSize, stoneRadius + fartherShadowSize);
        }
        g.setPaint(originalPaint);
    }

    /**
     * Draws a stone centered at (centerX, centerY)
     */
    private void drawStone(Graphics2D g, Graphics2D gShadow, int centerX, int centerY, Stone color) {
//        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
//                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // if no shadow graphics is supplied, just draw onto the same graphics
        if (gShadow == null)
            gShadow = g;

        switch (color) {
            case BLACK:
                drawShadow(gShadow, centerX, centerY, false);
                try {
                    g.drawImage(ImageIO.read(new File("assets/black0.png")), centerX - stoneRadius, centerY - stoneRadius, stoneRadius * 2 + 1, stoneRadius * 2 + 1, null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (blackStoneOutline) {
                    g.setColor(Color.WHITE);
                    g.drawOval(centerX - stoneRadius, centerY - stoneRadius, 2 * stoneRadius, 2 * stoneRadius);
                }
                break;

            case WHITE:
                drawShadow(gShadow, centerX, centerY, false);
                try {
                    g.drawImage(ImageIO.read(new File("assets/white0.png")), centerX - stoneRadius, centerY - stoneRadius, stoneRadius * 2 + 1, stoneRadius * 2 + 1, null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                g.setColor(Color.BLACK);
                g.drawOval(centerX - stoneRadius, centerY - stoneRadius, 2 * stoneRadius, 2 * stoneRadius);
                break;

            case BLACK_GHOST:
                drawShadow(gShadow, centerX, centerY, true);
                try {
                    g.drawImage(ImageIO.read(new File("assets/black0.png")), centerX - stoneRadius, centerY - stoneRadius, stoneRadius * 2 + 1, stoneRadius * 2 + 1, null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case WHITE_GHOST:
                drawShadow(gShadow, centerX, centerY, true);
                try {
                    g.drawImage(ImageIO.read(new File("assets/white0.png")), centerX - stoneRadius, centerY - stoneRadius, stoneRadius * 2 + 1, stoneRadius * 2 + 1, null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            default:
        }
    }

    /**
     * Fills in a circle centered at (centerX, centerY) with radius $radius$
     */
    private void fillCircle(Graphics2D g, int centerX, int centerY, int radius) {
        g.fillOval(centerX - radius, centerY - radius, 2 * radius + 1, 2 * radius + 1);
    }

    /**
     * Draws the outline of a circle centered at (centerX, centerY) with radius $radius$
     */
    private void drawCircle(Graphics2D g, int centerX, int centerY, int radius) {
        g.drawOval(centerX - radius, centerY - radius, 2 * radius + 1, 2 * radius + 1);
    }

    /**
     * Draws a string centered at (x, y) of font $fontString$, whose contents are $string$.
     * The maximum/default fontsize will be $maximumFontHeight$, and the length of the drawn string will be at most maximumFontWidth.
     * The resulting actual size depends on the length of $string$.
     * aboveOrBelow is a param that lets you set:
     * aboveOrBelow = -1 -> y is the top of the string
     * aboveOrBelow = 0  -> y is the vertical center of the string
     * aboveOrBelow = 1  -> y is the bottom of the string
     */
    private void drawString(Graphics2D g, int x, int y, String fontString, int style, String string, float maximumFontHeight, double maximumFontWidth, int aboveOrBelow) {

        Font font = makeFont(fontString, style);

        // set maximum size of font
        font = font.deriveFont((float) (font.getSize2D() * maximumFontWidth / g.getFontMetrics(font).stringWidth(string)));
        font = font.deriveFont(Math.min(maximumFontHeight, font.getSize()));
        g.setFont(font);

        FontMetrics metrics = g.getFontMetrics(font);

        int height = metrics.getAscent() - metrics.getDescent();
        int verticalOffset;
        switch (aboveOrBelow) {
            case -1:
                verticalOffset = height / 2;
                break;

            case 1:
                verticalOffset = -height / 2;
                break;

            default:
                verticalOffset = 0;
        }
        // bounding box for debugging
        // g.drawRect(x-(int)maximumFontWidth/2, y - height/2 + verticalOffset, (int)maximumFontWidth, height+verticalOffset );
        g.drawString(string, x - metrics.stringWidth(string) / 2, y + height / 2 + verticalOffset);
    }

    private void drawString(Graphics2D g, int x, int y, String fontString, String string, float maximumFontHeight, double maximumFontWidth) {
        drawString(g, x, y, fontString, Font.PLAIN, string, maximumFontHeight, maximumFontWidth, 0);
    }

    /**
     * @return a font with kerning enabled
     */
    private Font makeFont(String fontString, int style) {
        Font font = new Font(fontString, style, 100);
        Map<TextAttribute, Object> atts = new HashMap<>();
        atts.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        return font.deriveFont(atts);
    }


    /**
     * @return a shorter, rounded string version of playouts. e.g. 345 -> 345, 1265 -> 1.3k, 44556 -> 45k, 133523 -> 134k, 1234567 -> 1.2m
     */
    private String getPlayoutsString(int playouts) {
        if (playouts >= 1_000_000) {
            double playoutsDouble = (double) playouts / 100_000; // 1234567 -> 12.34567
            return Math.round(playoutsDouble) / 10.0 + "m";
        } else if (playouts >= 10_000) {
            double playoutsDouble = (double) playouts / 1_000; // 13265 -> 13.265
            return Math.round(playoutsDouble) + "k";
        } else if (playouts >= 1_000) {
            double playoutsDouble = (double) playouts / 100; // 1265 -> 12.65
            return Math.round(playoutsDouble) / 10.0 + "k";
        } else {
            return String.valueOf(playouts);
        }
    }


    private int[] calculatePixelMargins() {
        return calculatePixelMargins(boardLength);
    }

    /**
     * Set the location to render the board
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Set the maximum boardLength to render the board
     *
     * @param boardLength the boardLength of the board
     */
    public void setBoardLength(int boardLength) {
        this.boardLength = boardLength;
    }

    /**
     * Converts a location on the screen to a location on the board
     *
     * @param x x pixel coordinate
     * @param y y pixel coordinate
     * @return if there is a valid coordinate, an array (x, y) where x and y are between 0 and BOARD_SIZE - 1. Otherwise, returns null
     */
    public int[] convertScreenToCoordinates(int x, int y) {
        int marginLength; // the pixel width of the margins
        int boardLengthWithoutMargins; // the pixel width of the game board without margins

        // calculate a good set of boardLength, scaledMargin, and boardLengthWithoutMargins to use
        int[] calculatedPixelMargins = calculatePixelMargins();
        boardLength = calculatedPixelMargins[0];
        marginLength = calculatedPixelMargins[1];
        boardLengthWithoutMargins = calculatedPixelMargins[2];

        int squareSize = calculateSquareLength(boardLengthWithoutMargins);

        // transform the pixel coordinates to board coordinates
        x = (x - this.x - marginLength + squareSize / 2) / squareSize;
        y = (y - this.y - marginLength + squareSize / 2) / squareSize;

        // return these values if they are valid board coordinates
        if (Board.isValid(x, y))
            return new int[]{x, y};
        else
            return null;
    }

    /**
     * Calculate the boardLength of each intersection square
     *
     * @param availableLength the pixel board length of the game board without margins
     * @return the board length of each intersection square
     */
    private int calculateSquareLength(int availableLength) {
        return availableLength / (Board.BOARD_SIZE - 1);
    }
}