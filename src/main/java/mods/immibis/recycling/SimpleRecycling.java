package mods.immibis.recycling;

import ic2.api.recipe.RecipeInputItemStack;
import ic2.api.recipe.RecipeOutput;
import ic2.api.recipe.Recipes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.oredict.OreDictionary;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;

@Mod(modid="SimpleRecycling", name="Simple Recycling", version="58.0.0")
public class SimpleRecycling {
	
	private static class IC2 {
		public ItemStack getMacerated(ItemStack stack) {
			RecipeOutput out = Recipes.macerator.getOutputFor(stack, false);
			return out == null ? null : out.items.get(0);
		}

		public void addMaceratorRecipe(ItemStack in, ItemStack out) {
			Recipes.macerator.addRecipe(new RecipeInputItemStack(in), null, out);
		}
	}
	
	private static class BlockMetaPair {
		public Item item;
		public int data;
		
		public BlockMetaPair(Item item, int k) {
			this.item = item;
			this.data = k;
		}

		public static BlockMetaPair parse(String s) {
			if(s.split(":").length < 2) {
				System.out.println("Invalid name:meta block format! " + s);
				return null;
			}
			String name = s.substring(0, s.lastIndexOf(":"));
			String meta = s.substring(s.lastIndexOf(":") + 1);
			return new BlockMetaPair((Item)Item.itemRegistry.getObject(name), Integer.parseInt(meta));
		}
		
		public ItemStack toItemStack() {
			return new ItemStack(item, 1, data);
		}
	}
	
	private static class ToolEntry {
		public BlockMetaPair tool, mat;
		public int matAmt;
		public boolean smeltable;
		
		public ToolEntry(BlockMetaPair tool, BlockMetaPair mat, int matAmt, boolean smeltable) {
			this.tool = tool;
			this.mat = mat;
			this.matAmt = matAmt;
			this.smeltable = smeltable;
		}
		
		public ToolEntry(Item tool, int toolMeta, Item mat, int matMeta, int matAmt, boolean smeltable) {
			this(new BlockMetaPair(tool, toolMeta), new BlockMetaPair(mat, matMeta), matAmt, smeltable);
		}

		public static ToolEntry parse(String s) {
			String[] a = s.split(",");
			try {
				if(a.length != 4)
					throw new NumberFormatException();
				if(!a[3].equals("Y") && !a[3].equals("N"))
					throw new NumberFormatException();
				return new ToolEntry(BlockMetaPair.parse(a[0]), BlockMetaPair.parse(a[1]), Integer.parseInt(a[2]), a[3].equals("Y")); 
			} catch(NumberFormatException e) {
				throw new RuntimeException("Not a valid tool entry (format is toolName:toolMeta,materialName:materialMeta,materialAmount,smeltable where smeltable is Y or N): " + s);
			}
		}
	}
	
	List<BlockMetaPair> ingots = new ArrayList<BlockMetaPair>();
	List<BlockMetaPair> gems = new ArrayList<BlockMetaPair>();
	List<ToolEntry> tools = new ArrayList<ToolEntry>();
	boolean smelt, macerate;
	IC2 ic2;
	
	@EventHandler
	public void onPreInit(FMLPreInitializationEvent evt) {
		Configuration c = new Configuration(evt.getSuggestedConfigurationFile());
		c.load();
		
		boolean dirty = false;
		
		Property ingotsProp = c.get(Configuration.CATEGORY_GENERAL, "ingots", "");
		String COMMENT = "List of name:meta pairs, separated by semicolons. Example: ingot:2;anotherIngot:100";
		if(!COMMENT.equals(ingotsProp.comment)) {
			ingotsProp.comment = COMMENT;
			dirty = true;
		}
		
		Property gemsProp = c.get(Configuration.CATEGORY_GENERAL, "gems", "");
		COMMENT = "List of name:meta pairs, separated by semicolons. Example: gem:2;anotherGem:100";
		if(!COMMENT.equals(gemsProp.comment)) {
			gemsProp.comment = COMMENT;
			dirty = true;
		}
		
		Property toolsProp = c.get(Configuration.CATEGORY_GENERAL, "tools", "");
		COMMENT = "Semicolon-separated list of toolName:toolMeta,materialName:materialMeta,materialAmount,smeltable. Example: ironPickaxe:0,ironIngot:0,3,Y";
		if(!COMMENT.equals(toolsProp.comment)) {
			toolsProp.comment = COMMENT;
			dirty = true;
		}
		
		
		for(String p : ingotsProp.getString().split(";"))
			if(!p.equals(""))
				ingots.add(BlockMetaPair.parse(p));
		
		for(String p : gemsProp.getString().split(";"))
			if(!p.equals(""))
				gems.add(BlockMetaPair.parse(p));
		
		for(String p : toolsProp.getString().split(";"))
			if(!p.equals(""))
				tools.add(ToolEntry.parse(p));
		
		smelt = c.get(Configuration.CATEGORY_GENERAL, "allowSmelting", true).getBoolean(true);
		macerate = c.get(Configuration.CATEGORY_GENERAL, "allowMacerating", true).getBoolean(true);
		
		if(dirty || c.hasChanged())
			c.save();
	}
	
	@EventHandler
	public void onPostInit(FMLPostInitializationEvent evt) {
		InventoryCrafting ic = new InventoryCrafting(new Container() {
			@Override
			public boolean canInteractWith(EntityPlayer entityplayer) {
				return false;
			}
		}, 3, 3);
		
		if(Loader.isModLoaded("IC2"))
			ic2 = new IC2();
		
		for(BlockMetaPair bmp : ingots) register(ic, true, bmp.toItemStack());
		for(BlockMetaPair bmp : gems) register(ic, true, bmp.toItemStack());
		
		for(String oreName : OreDictionary.getOreNames()) {
			List<ItemStack> ores = OreDictionary.getOres(oreName);
			if(ores.size() == 0)
				continue;
			
			if(oreName.startsWith("ingot"))
				register(ic, true, ores.get(0));
			else
				register(ic, false, ores.get(0));
		}
		
		register(ic, false, new ItemStack(Items.diamond));
		
		for(ToolEntry t : tools)
			register(t.smeltable, t.tool.toItemStack(), t.mat.toItemStack(), t.matAmt);
	}
	
	private void register(InventoryCrafting ic, boolean canSmelt, ItemStack ingotStack, String pattern) {
		int numIngots = 0;
		for(int k = 0; k < 9; k++)
		{
			char c = pattern.charAt(k);
			if(c == '#') {
				ic.setInventorySlotContents(k, ingotStack);
				numIngots++;
			} else if(c == ' ')
				ic.setInventorySlotContents(k, null);
			else if(c == '|')
				ic.setInventorySlotContents(k, new ItemStack(Items.stick));
			else
				throw new RuntimeException("pattern char "+c);
		}
		
		ItemStack craftedItem;
		try {
			craftedItem = CraftingManager.getInstance().findMatchingRecipe(ic, null);
		} catch(Exception e) {
			new Exception("[Simple Recycling] Caught exception looking up recipe for item '"+ingotStack+"' pattern '"+pattern+"'", e).printStackTrace();
			return;
		}
		
		if(craftedItem == null)
			return;
		
		register(canSmelt, craftedItem, ingotStack, numIngots);
	}
	
	private void register(boolean canSmelt, ItemStack craftedItem, ItemStack ingotStack, int numIngots) {
		// add smelting recipe, if smelting recipes are enabled, this item is smeltable, and the recipe doesn't already exist
		if(smelt && canSmelt && FurnaceRecipes.smelting().getSmeltingResult(craftedItem) == null) {
			ItemStack smeltingOutput = ingotStack.copy();
			smeltingOutput.stackSize = numIngots;
			
			//if(craftedItem.getItem().isDamageable())
			//	FurnaceRecipes.smelting().addSmelting(craftedItem.itemID, smeltingOutput, 0);
			//else
				GameRegistry.addSmelting(craftedItem, smeltingOutput, 0);
		}
		
		if(macerate && ic2 != null && ic2.getMacerated(craftedItem) == null) {
			ItemStack maceratingOutput = ic2.getMacerated(ingotStack);
			if(maceratingOutput != null) {
				maceratingOutput = maceratingOutput.copy();
				maceratingOutput.stackSize = numIngots;
				
				ic2.addMaceratorRecipe(craftedItem, maceratingOutput);
			}
		}
	}
	
	private void register(InventoryCrafting ic, boolean canSmelt, ItemStack ingotStack) {
		register(ic, canSmelt, ingotStack, "### |  | "); // pickaxe
		register(ic, canSmelt, ingotStack, " #  |  | "); // shovel
		register(ic, canSmelt, ingotStack, "## #|  | "); // axe (1)
		register(ic, canSmelt, ingotStack, " ## |# | "); // axe (2)
		register(ic, canSmelt, ingotStack, " #  #  | "); // sword
		register(ic, canSmelt, ingotStack, "##  |  | "); // hoe (1)
		register(ic, canSmelt, ingotStack, " ## |  | "); // hoe (2)
		register(ic, canSmelt, ingotStack, "     # # "); // shears (1)
		register(ic, canSmelt, ingotStack, "    #   #"); // shears (2)
		register(ic, canSmelt, ingotStack, " #   #|# "); // sickle
		register(ic, canSmelt, ingotStack, "#### ## #"); // leggings
		register(ic, canSmelt, ingotStack, "#### #   "); // helmet
		register(ic, canSmelt, ingotStack, "# #######"); // chestplate
		register(ic, canSmelt, ingotStack, "# ## #   "); // boots
	}
}
