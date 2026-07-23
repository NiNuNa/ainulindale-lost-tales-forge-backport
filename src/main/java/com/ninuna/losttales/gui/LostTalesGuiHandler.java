package com.ninuna.losttales.gui;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.inventory.container.LostTalesContainerMissiveBoard;
import com.ninuna.losttales.inventory.container.LostTalesContainerWaystone;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneSettingsService;
import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Server/client GUI bridge for Forge 1.7.10.
 *
 * This common-side handler creates only server-safe containers directly. Client
 * GUI construction is delegated through the active proxy so dedicated servers do
 * not need to load client-only GUI classes.
 */
public class LostTalesGuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == LostTalesGuiIds.MISSIVE_BOARD) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof LostTalesTileEntityMissiveBoard) {
                LostTalesTileEntityMissiveBoard board = (LostTalesTileEntityMissiveBoard) tileEntity;
                if (board.isUseableByPlayer(player)) {
                    return new LostTalesContainerMissiveBoard(player.inventory, board);
                }
            }
        }
        if (id == LostTalesGuiIds.WAYSTONE
                && player instanceof EntityPlayerMP) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof LostTalesTileEntityWaystone) {
                LostTalesTileEntityWaystone waystone =
                        (LostTalesTileEntityWaystone)tileEntity;
                LostTalesMapMarkerRecord record =
                        LostTalesMapMarkerStorage.get(world)
                                .getRecord(waystone.getMarkerId());
                if (waystone.isUseableByPlayer(player)
                        && waystone.isLinked()
                        && record != null
                        && record.isLinked()
                        && record.getLinkToken() != null
                        && record.getLinkToken().equals(
                                waystone.getLinkToken())
                        && record.getLinkedDimensionId()
                                == world.provider.dimensionId
                        && record.getLinkedX() == x
                        && record.getLinkedY() == y
                        && record.getLinkedZ() == z) {
                    LostTalesWaystoneSettingsService.sendState(
                            (EntityPlayerMP)player, waystone, record);
                    return new LostTalesContainerWaystone(waystone);
                }
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return LostTalesMod.proxy.getClientGuiElement(id, player, world, x, y, z);
    }
}
