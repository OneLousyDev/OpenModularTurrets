package openmodularturrets.blocks.turretheads;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import openmodularturrets.reference.Names;
import openmodularturrets.tileentity.turrets.GrenadeLauncherTurretTileEntity;

public class BlockGrenadeTurret extends BlockAbstractTurretHead {
    public BlockGrenadeTurret() {
        super();

        this.setUnlocalizedName(Names.Blocks.grenadeTurret);
    }

    @Override
    public TileEntity createNewTileEntity(World p_149915_1_, int p_149915_2_) {
        return new GrenadeLauncherTurretTileEntity();
    }
}
