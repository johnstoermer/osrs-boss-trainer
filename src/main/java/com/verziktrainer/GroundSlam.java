package com.verziktrainer;

import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * A single ground slam tile effect that appears after a delay and fades after a duration.
 */
public class GroundSlam
{
    private static final int GROUND_SLAM_MODEL = 52613;
    private static final int GROUND_SLAM_ANIM = 10830;
    private static final int VISIBLE_TICKS = 3;

    private final RuneLiteObject object;
    private final WorldPoint tile;
    private int delayTicks;
    private int remainingTicks;
    private boolean activated;
    private boolean justActivated;

    public GroundSlam(Client client, WorldPoint tile, int delayTicks)
    {
        this.tile = tile;
        this.delayTicks = delayTicks;
        this.remainingTicks = VISIBLE_TICKS;
        this.activated = false;
        this.justActivated = false;

        WorldView worldView = client.getTopLevelWorldView();
        LocalPoint lp = LocalPoint.fromWorld(worldView, tile);

        net.runelite.api.ModelData modelData = client.loadModelData(GROUND_SLAM_MODEL);
        Model model = modelData != null ? modelData.light() : null;
        net.runelite.api.Animation anim = client.loadAnimation(GROUND_SLAM_ANIM);

        object = client.createRuneLiteObject();
        if (model != null)
        {
            object.setModel(model);
        }
        if (anim != null)
        {
            object.setAnimation(anim);
            object.setShouldLoop(false);
        }
        if (lp != null)
        {
            object.setLocation(lp, worldView.getPlane());
        }
        if (delayTicks <= 0)
        {
            // No delay — activate immediately
            this.delayTicks = 0;
            this.activated = true;
            this.justActivated = true;
            object.setActive(true);
        }
        else
        {
            object.setActive(false);
        }
    }

    /**
     * Called each game tick. Returns false when this slam should be removed.
     */
    public boolean tick()
    {
        if (delayTicks > 0)
        {
            delayTicks--;
            if (delayTicks == 0)
            {
                activated = true;
                justActivated = true;
                object.setActive(true);
            }
            return true;
        }

        remainingTicks--;
        if (remainingTicks <= 0)
        {
            object.setActive(false);
            return false;
        }
        return true;
    }

    public void cleanup()
    {
        object.setActive(false);
    }

    public boolean isActivated()
    {
        return activated;
    }

    public boolean isJustActivated()
    {
        return justActivated;
    }

    public void clearJustActivated()
    {
        justActivated = false;
    }

    public WorldPoint getTile()
    {
        return tile;
    }
}
