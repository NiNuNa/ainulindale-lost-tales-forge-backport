package com.ninuna.losttales.config.client;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;

/**
 * An option that takes one of a few fixed words, offered as a button that
 * steps through them rather than a box to type one into: a click moves
 * to the next word and a right-click back to the one before, as the
 * chat's channel button does. Forge's own cycling entry is this without
 * the step back, and its constructor is private, so it cannot be
 * extended.
 */
public class LostTalesCycleEntry extends GuiConfigEntries.ButtonEntry {
    private final int beforeIndex;
    private final int defaultIndex;
    private int currentIndex;

    public LostTalesCycleEntry(GuiConfig owningScreen,
                               GuiConfigEntries owningEntryList,
                               IConfigElement<?> configElement) {
        super(owningScreen, owningEntryList, configElement);
        this.beforeIndex = indexOf(String.valueOf(configElement.get()));
        this.defaultIndex = indexOf(String.valueOf(configElement.getDefault()));
        this.currentIndex = this.beforeIndex;
        this.btnValue.enabled = enabled();
        updateValueButtonText();
    }

    /**
     * Offers every option of the category that is one word out of a set
     * it names with this entry.
     */
    static void offerChoices(ConfigCategory category) {
        if (category == null) {
            return;
        }
        for (Property property : category.values()) {
            String[] choices = property.getValidValues();
            if (property.getType() == Property.Type.STRING
                    && !property.isList() && choices != null
                    && choices.length > 0) {
                property.setConfigEntryClass(LostTalesCycleEntry.class);
            }
        }
    }

    /** Where a word stands among the choices, case aside; the first for any other. */
    private int indexOf(String value) {
        String[] choices = this.configElement.getValidValues();
        String wanted = value == null ? "" : value.trim();
        for (int index = 0; index < choices.length; index++) {
            if (choices[index].equalsIgnoreCase(wanted)) {
                return index;
            }
        }
        return 0;
    }

    private void step(int by) {
        if (!enabled()) {
            return;
        }
        int count = this.configElement.getValidValues().length;
        this.currentIndex = ((this.currentIndex + by) % count + count) % count;
        updateValueButtonText();
    }

    @Override
    public void updateValueButtonText() {
        this.btnValue.displayString = I18n.format(
                this.configElement.getValidValues()[this.currentIndex]);
    }

    @Override
    public void valueButtonPressed(int slotIndex) {
        step(1);
    }

    /**
     * A right-click on the button steps back. The list hands every entry
     * each click it does not route to one itself, so only the entry the
     * list finds under the pointer answers.
     */
    @Override
    public void mouseClicked(int x, int y, int mouseEvent) {
        if (mouseEvent == 1
                && this.owningEntryList.func_148124_c(x, y)
                        == this.owningEntryList.listEntries.indexOf(this)
                && this.btnValue.mousePressed(this.mc, x, y)) {
            this.btnValue.func_146113_a(this.mc.getSoundHandler());
            step(-1);
        }
    }

    @Override
    public boolean isDefault() {
        return this.currentIndex == this.defaultIndex;
    }

    @Override
    public void setToDefault() {
        if (enabled()) {
            this.currentIndex = this.defaultIndex;
            updateValueButtonText();
        }
    }

    @Override
    public boolean isChanged() {
        return this.currentIndex != this.beforeIndex;
    }

    @Override
    public void undoChanges() {
        if (enabled()) {
            this.currentIndex = this.beforeIndex;
            updateValueButtonText();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean saveConfigElement() {
        if (enabled() && isChanged()) {
            this.configElement.set(getCurrentValue());
            return this.configElement.requiresMcRestart();
        }
        return false;
    }

    @Override
    public String getCurrentValue() {
        return this.configElement.getValidValues()[this.currentIndex];
    }

    @Override
    public String[] getCurrentValues() {
        return new String[] {getCurrentValue()};
    }
}
