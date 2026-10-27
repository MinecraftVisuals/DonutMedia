package com.mediatag;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class MediaTagMod implements ClientModInitializer {

    private static final Identifier ICON =
            Identifier.of("mediatag", "textures/gui/media.png");

    // actual size of media.png in pixels
    private static final int TEX_W = 104;
    private static final int TEX_H = 72;

    // how big the icon is drawn, in "text pixels" (a normal nametag line is 9 tall)
    private static final int ICON_W = 10;
    private static final int ICON_H = 7;

    private static final int GAP = 1;      // space between icon and the start of the name
    private static final int ICON_Y = 1;   // nudge icon up (-) or down (+) if it looks off

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

        // vanilla nametags are 0.025 blocks per text pixel, no clamping -> same size as the real tag
        float scale = (float) (0.025 * pxPerBlock);

        // only used to find where the left edge of the real nametag is
        int nameW = mc.textRenderer.getWidth(player.getDisplayName());
        int iconX = -(nameW / 2) - GAP - ICON_W;

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(sx, sy);
        m.scale(scale, scale);

        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ICON,
                iconX, ICON_Y,
                0f, 0f,
                ICON_W, ICON_H,
                TEX_W, TEX_H,
                TEX_W, TEX_H);

        m.popMatrix();
    }
}
