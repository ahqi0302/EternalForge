package tw.eternalforge.gui;
import org.bukkit.Material;import org.bukkit.inventory.ItemStack;import org.bukkit.inventory.meta.ItemMeta;import tw.eternalforge.util.Text;import java.util.List;
final class IconFactory{ItemStack make(Material m,String name,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();if(meta!=null){meta.setDisplayName(Text.c(name));meta.setLore(Text.c(lore));i.setItemMeta(meta);}return i;}}
