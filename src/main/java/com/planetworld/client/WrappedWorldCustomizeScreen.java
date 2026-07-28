package com.planetworld.client;

import com.planetworld.config.PlanetSettings;
import com.planetworld.config.PlanetSettingsAccess;
import com.planetworld.config.WorldGenStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WrappedWorldCustomizeScreen extends Screen {
    private final CreateWorldScreen parent;
    private PlanetSettings settings;
    private int circumferenceStepIndex;
    private Component status = CommonComponents.EMPTY;

    public WrappedWorldCustomizeScreen(CreateWorldScreen parent) {
        super(Component.translatable("planetworld.customize.title"));
        this.parent = parent;
        PlanetSettings pending = PlanetSettingsAccess.getPending();
        this.settings = pending != null ? pending : PlanetSettings.defaults();
        this.circumferenceStepIndex = PlanetSettings.stepIndex(this.settings.circumference());
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 40;

        this.addRenderableWidget(new CircumferenceSlider(
                centerX - 100, y + 24, 200, 20,
                this.circumferenceStepIndex
        ));

        y += 72;
        this.addRenderableWidget(CycleButton.builder(this::styleLabel)
                .withValues(WorldGenStyle.NORMAL, WorldGenStyle.COMPLETE)
                .withInitialValue(this.settings.worldGenStyle())
                .create(centerX - 110, y, 220, 20,
                        Component.translatable("planetworld.customize.world_gen_style"),
                        (b, value) -> {
                            this.status = CommonComponents.EMPTY;
                            this.settings = this.settings.withWorldGenStyle(value);
                        }));

        y += 28;
        this.addRenderableWidget(CycleButton.onOffBuilder(this.settings.curvatureShader())
                .create(centerX - 110, y, 220, 20,
                        Component.translatable("planetworld.customize.curvature"),
                        (b, value) -> this.settings = this.settings.withCurvatureShader(value)));

        y += 24;
        this.addRenderableWidget(CycleButton.onOffBuilder(this.settings.localizedTime())
                .create(centerX - 110, y, 220, 20,
                        Component.translatable("planetworld.customize.localized_time"),
                        (b, value) -> this.settings = this.settings.withLocalizedTime(value)));

        y += 24;
        this.addRenderableWidget(CycleButton.onOffBuilder(this.settings.localizedWeather())
                .create(centerX - 110, y, 220, 20,
                        Component.translatable("planetworld.customize.localized_weather"),
                        (b, value) -> this.settings = this.settings.withLocalizedWeather(value)));

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onDone())
                .bounds(centerX - 155, this.height - 28, 150, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
                .bounds(centerX + 5, this.height - 28, 150, 20).build());
    }

    private Component styleLabel(WorldGenStyle style) {
        return Component.translatable("planetworld.customize.world_gen_style." + style.name().toLowerCase());
    }

    private void onDone() {
        int circumference = PlanetSettings.CIRCUMFERENCE_STEPS[this.circumferenceStepIndex];
        this.settings = this.settings.withCircumference(circumference);
        PlanetSettingsAccess.setPending(this.settings);
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, 15, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("planetworld.customize.circumference"),
                centerX, 40, 0xA0A0A0);
        graphics.drawCenteredString(this.font, Component.translatable(
                        "planetworld.customize.circumference.hint",
                        PlanetSettings.MIN_CIRCUMFERENCE,
                        PlanetSettings.MAX_CIRCUMFERENCE),
                centerX, 52, 0x808080);

        int circumference = PlanetSettings.CIRCUMFERENCE_STEPS[this.circumferenceStepIndex];
        graphics.drawCenteredString(this.font, Component.translatable(
                        "planetworld.customize.chunks",
                        Math.max(1, circumference / 8),
                        circumference),
                centerX, 88, 0xC0C0C0);

        float pct = PlanetSettings.physicalCurvePercentAt(circumference, 64.0);
        graphics.drawCenteredString(this.font, Component.translatable(
                        "planetworld.customize.curvature_auto",
                        String.format("%.1f", pct)),
                centerX, 100, 0xA0A0A0);

        if (this.settings.worldGenStyle() == WorldGenStyle.COMPLETE) {
            graphics.drawCenteredString(this.font, Component.translatable("planetworld.customize.complete_hint"),
                    centerX, 112, 0x808080);
        }

        if (this.status != CommonComponents.EMPTY) {
            graphics.drawCenteredString(this.font, this.status, centerX, this.height - 50, 0xFF5555);
        }
    }

    private final class CircumferenceSlider extends AbstractSliderButton {
        CircumferenceSlider(int x, int y, int width, int height, int stepIndex) {
            super(x, y, width, height, CommonComponents.EMPTY,
                    stepIndex / (double) (PlanetSettings.CIRCUMFERENCE_STEPS.length - 1));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = PlanetSettings.CIRCUMFERENCE_STEPS[WrappedWorldCustomizeScreen.this.circumferenceStepIndex];
            this.setMessage(Component.translatable("planetworld.customize.circumference.value", value));
        }

        @Override
        protected void applyValue() {
            int last = PlanetSettings.CIRCUMFERENCE_STEPS.length - 1;
            int index = (int) Math.round(this.value * last);
            index = Math.max(0, Math.min(last, index));
            WrappedWorldCustomizeScreen.this.circumferenceStepIndex = index;
            this.value = index / (double) last;
            updateMessage();
            WrappedWorldCustomizeScreen.this.status = CommonComponents.EMPTY;
        }
    }
}
