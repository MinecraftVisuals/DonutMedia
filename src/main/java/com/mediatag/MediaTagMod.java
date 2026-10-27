package com.mediatag;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class MediaTagMod implements ClientModInitializer {

    // '#' = pink body, 'W' = white viewfinder window, '.' = empty
    private static final String[] ICON = {
        "................#.",
        "..............###.",
        "..............###.",
        "...##############.",
        "#######WWWWW#####.",
        "#######WWWWW###...",
        "###############...",
        "###############...",
        "###############...",
        "###############...",
        "###############...",
        "...############...",
    };

    private static final int PINK = 0xFFFF10A0;
    private static final int SHADOW = 0xFF7A0A50;
    private static final int WHITE = 0xFFFFFFFF;

    private static final float ICON_H = 10f;                    // icon height in text units
    private static final float CELL = ICON_H / ICON.length;     // size of one icon pixel
    private static final int ICON_W = 15;                       // icon width in text units
    private static final int GAP = 3;                           // space between icon and name

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(MediaTagMod::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.options.hudHidden) return;
        if (mc.options.getPerspective().isFirstPerson()) return;

        float delta = tickCounter.getTickProgress(false);
        Camera cam = mc.gameRenderer.getCamera();

        // camera basis vectors
        double yaw = Math.toRadians(cam.getYaw());
        double pitch = Math.toRadians(cam.getPitch());
        double cp = Math.cos(pitch);
        if (cp < 1e-4) return;
        double fx = -Math.sin(yaw) * cp;
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * cp;
        double rx = -fz / cp;
        double rz = fx / cp;
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;

        // camera position (3rd person camera sits 4 blocks behind the eye, clipped by blocks)
        Vec3d eye = player.getCameraPosVec(delta);
        double dist = 4.0;
        Vec3d back = eye.add(-fx * 4.0, -fy * 4.0, -fz * 4.0);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, back, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) {
            dist = Math.max(0.3, hit.getPos().distanceTo(eye) - 0.2);
        }
        Vec3d camPos = eye.subtract(fx * dist, fy * dist, fz * dist);

        // name tag position (same spot vanilla uses: above the head)
        Vec3d anchor = player.getLerpedPos(delta).add(0, player.getHeight() + 0.5, 0);
        double vx = anchor.x - camPos.x;
        double vy = anchor.y - camPos.y;
        double vz = anchor.z - camPos.z;
        double xc = vx * rx + vz * rz;
        double yc = vx * ux + vy * uy + vz * uz;
        double zc = vx * fx + vy * fy + vz * fz;
        if (zc < 0.1) return;

        double tan = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        double pxPerBlock = (h / 2.0) / (zc * tan);
        float sx = (float) (w / 2.0 + xc * pxPerBlock);
        float sy = (float) (h / 2.0 - yc * pxPerBlock);
        float scale = (float) MathHelper.clamp(0.025 * pxPerBlock, 0.5, 3.0);

        Text name = player.getDisplayName();
        int nameW = mc.textRenderer.getWidth(name);
        int total = ICON_W + GAP + nameW;
        int left = -(total / 2); // icon + name centered as one group

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(sx, sy);
        m.scale(scale, scale);
        m.translate(0f, -4.5f);

        // background
        ctx.fill(left - 2, -1, left + total + 2, 10, 0x40000000);

        // icon
        m.pushMatrix();
        m.translate((float) left, -0.5f);
        m.scale(CELL, CELL);
        m.pushMatrix();
        m.translate(0.5f, 0.5f);
        paint(ctx, SHADOW, 'A');
        m.popMatrix();
        paint(ctx, PINK, 'A');
        paint(ctx, WHITE, 'W');
        m.popMatrix();

        // name
        ctx.drawText(mc.textRenderer, name, left + ICON_W + GAP, 0, 0xFFFFFFFF, false);

        m.popMatrix();
    }

    private static boolean match(char c, char only) {
        return only == 'W' ? c == 'W' : c != '.';
    }

    private static void paint(DrawContext ctx, int color, char only) {
        for (int y = 0; y < ICON.length; y++) {
            String row = ICON[y];
            int x = 0;
            while (x < row.length()) {
                if (!match(row.charAt(x), only)) { x++; continue; }
                int start = x;
                while (x < row.length() && match(row.charAt(x), only)) x++;
                ctx.fill(start, y, x, y + 1, color);
            }
        }
    }
}
