package com.verziktrainer;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * Tile patterns for Sol Heredit's ground slam attacks.
 * Patterns are defined relative to the boss center and the cardinal direction toward the player.
 */
public class TilePatterns
{
    /**
     * Spear 1: Two full-width lines at rows 1,3 (center line and offset),
     * plus hazard tiles at column 0 for rows 0,2,4.
     * Pattern (rows=perpendicular, cols=distance from boss edge):
     * -****
     * -----
     * -****
     * -----
     * -****
     */
    public static List<Map.Entry<WorldPoint, Integer>> getSpear1(
        WorldPoint bossCenter, int dirX, int dirY, int bossHalfSize)
    {
        List<Map.Entry<WorldPoint, Integer>> tiles = new ArrayList<>();

        // Perpendicular direction
        int perpX = -dirY;
        int perpY = dirX;

        // 5 rows perpendicular, 5 cols extending from boss
        for (int row = -2; row <= 2; row++)
        {
            boolean fullRow = (row == -1 || row == 1); // rows 1 and 3 (offset from center)
            for (int col = 1; col <= 5; col++)
            {
                if (fullRow || col == 1)
                {
                    int tileX = bossCenter.getX() + dirX * (bossHalfSize + col) + perpX * row;
                    int tileY = bossCenter.getY() + dirY * (bossHalfSize + col) + perpY * row;
                    int delay = 0;
                    tiles.add(new AbstractMap.SimpleEntry<>(
                        new WorldPoint(tileX, tileY, bossCenter.getPlane()), delay));
                }
            }
        }

        // Also fill under the boss
        addBossTiles(tiles, bossCenter, bossHalfSize);

        return tiles;
    }

    /**
     * Spear 2: Two full-width lines at rows 0,2,4,
     * plus hazard tiles at column 0 for rows 1,3.
     * Pattern:
     * -----
     * -****
     * -----
     * -****
     * -----
     */
    public static List<Map.Entry<WorldPoint, Integer>> getSpear2(
        WorldPoint bossCenter, int dirX, int dirY, int bossHalfSize)
    {
        List<Map.Entry<WorldPoint, Integer>> tiles = new ArrayList<>();

        int perpX = -dirY;
        int perpY = dirX;

        for (int row = -2; row <= 2; row++)
        {
            boolean fullRow = (row == -2 || row == 0 || row == 2); // rows 0, 2, 4
            for (int col = 1; col <= 5; col++)
            {
                if (fullRow || col == 1)
                {
                    int tileX = bossCenter.getX() + dirX * (bossHalfSize + col) + perpX * row;
                    int tileY = bossCenter.getY() + dirY * (bossHalfSize + col) + perpY * row;
                    int delay = 0;
                    tiles.add(new AbstractMap.SimpleEntry<>(
                        new WorldPoint(tileX, tileY, bossCenter.getPlane()), delay));
                }
            }
        }

        addBossTiles(tiles, bossCenter, bossHalfSize);

        return tiles;
    }

    /**
     * Shield 1: Concentric rings around boss. Hazard at ring 0, safe at ring 1,
     * hazard at rings 2-4.
     * Cross-section: - * - - -
     */
    public static List<Map.Entry<WorldPoint, Integer>> getShield1(
        WorldPoint bossCenter, int bossHalfSize)
    {
        List<Map.Entry<WorldPoint, Integer>> tiles = new ArrayList<>();
        int safeRing = 1;

        for (int ring = 0; ring <= 4; ring++)
        {
            if (ring == safeRing)
            {
                continue;
            }
            int dist = bossHalfSize + 1 + ring;
            addRing(tiles, bossCenter, dist, 0);
        }

        addBossTiles(tiles, bossCenter, bossHalfSize);

        return tiles;
    }

    /**
     * Shield 2: Concentric rings around boss. Hazard at rings 0-1, safe at ring 2,
     * hazard at rings 3-4.
     * Cross-section: - - * - -
     */
    public static List<Map.Entry<WorldPoint, Integer>> getShield2(
        WorldPoint bossCenter, int bossHalfSize)
    {
        List<Map.Entry<WorldPoint, Integer>> tiles = new ArrayList<>();
        int safeRing = 2;

        for (int ring = 0; ring <= 4; ring++)
        {
            if (ring == safeRing)
            {
                continue;
            }
            int dist = bossHalfSize + 1 + ring;
            addRing(tiles, bossCenter, dist, 0);
        }

        addBossTiles(tiles, bossCenter, bossHalfSize);

        return tiles;
    }

    /**
     * Adds a square ring of tiles at the given distance from the center.
     */
    private static void addRing(List<Map.Entry<WorldPoint, Integer>> tiles,
        WorldPoint center, int dist, int delay)
    {
        for (int i = -dist; i <= dist; i++)
        {
            // Top and bottom edges
            tiles.add(new AbstractMap.SimpleEntry<>(
                new WorldPoint(center.getX() + i, center.getY() + dist, center.getPlane()), delay));
            tiles.add(new AbstractMap.SimpleEntry<>(
                new WorldPoint(center.getX() + i, center.getY() - dist, center.getPlane()), delay));
        }
        for (int i = -dist + 1; i < dist; i++)
        {
            // Left and right edges (excluding corners already added)
            tiles.add(new AbstractMap.SimpleEntry<>(
                new WorldPoint(center.getX() + dist, center.getY() + i, center.getPlane()), delay));
            tiles.add(new AbstractMap.SimpleEntry<>(
                new WorldPoint(center.getX() - dist, center.getY() + i, center.getPlane()), delay));
        }
    }

    /**
     * Adds hazard tiles under the boss area.
     */
    private static void addBossTiles(List<Map.Entry<WorldPoint, Integer>> tiles,
        WorldPoint center, int halfSize)
    {
        for (int x = -halfSize; x <= halfSize; x++)
        {
            for (int y = -halfSize; y <= halfSize; y++)
            {
                tiles.add(new AbstractMap.SimpleEntry<>(
                    new WorldPoint(center.getX() + x, center.getY() + y, center.getPlane()), 0));
            }
        }
    }

    /**
     * Get the cardinal direction (N/S/E/W) from boss to player.
     * Returns {dirX, dirY} where exactly one is non-zero.
     */
    public static int[] getCardinalDirection(WorldPoint from, WorldPoint to)
    {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();

        // Pick the dominant axis
        if (Math.abs(dx) >= Math.abs(dy))
        {
            return new int[]{Integer.signum(dx), 0};
        }
        else
        {
            return new int[]{0, Integer.signum(dy)};
        }
    }
}
