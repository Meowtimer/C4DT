package net.arctics.clonk.mapcreator;

import java.io.StringReader;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.arctics.clonk.Core;
import net.arctics.clonk.ini.IntegerArray;
import net.arctics.clonk.ini.MaterialUnit;
import net.arctics.clonk.util.StreamUtil;
import net.arctics.clonk.util.StringUtil;

import org.eclipse.core.resources.IFile;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;

public class TextureMap extends HashMap<String, Integer> {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	public static final int C4M_MaxTexIndex = 127;
	public static final String TEXMAP_FILE = "TEXMAP.txt";
	
	private final PaletteData palette;
	
	public PaletteData palette() { return palette; }
	
	public TextureMap() {
		palette = null;
	}
	
	
	public TextureMap(final IFile texMapFile, final MaterialMap materials) {
		if (texMapFile == null)
			throw new IllegalArgumentException();
		final RGB[] colors = new RGB[256];
		colors[0] =  new RGB(225, 243, 255);
		final Pattern linePattern = Pattern.compile("([0-9]+)\\=(\\w+)\\-(\\w+)");
		final Matcher lineMatcher = linePattern.matcher("");
		for (String line : StringUtil.lines(new StringReader(StreamUtil.stringFromFile(texMapFile)))) {
			line = line.trim();
			if (line.startsWith("#"))
				continue;
			if (lineMatcher.reset(line).matches()) {
				final int index = Integer.parseInt(lineMatcher.group(1));
				final String material = lineMatcher.group(2);
				final String texture = lineMatcher.group(3);
				final MaterialUnit unit = materials.get(material);
				RGB color = null;
				if (unit != null) {
					final IntegerArray v = unit.complexValue("Material.Color", IntegerArray.class);
					if (v != null && v.values().length >= 3)
						color = new RGB(
							v.values()[0].summedValue(),
							v.values()[1].summedValue(),
							v.values()[2].summedValue()
						);
				}
				colors[index] = color;
				colors[index+128] = color;
				this.put(material+"-"+texture, index);
			}
		}
		final RGB nullColor = new RGB(0, 0, 0);
		for (int i = 0; i < colors.length; i++)
			if (colors[i] == null)
				colors[i] = nullColor;
		this.palette = new PaletteData(colors);
	}
	
	public int GetIndex(final String szMaterial, final String szTexture, final boolean fAddIfNotExist)
	{
		// Find existing
		final String combo = szMaterial+"-"+szTexture;
		Integer byIndex = get(combo);
		if (byIndex != null)
			return byIndex;
		// Add new entry
		if (fAddIfNotExist) {
			byIndex = size()+1;
			put(combo, byIndex);
			return byIndex;
		}
		return 0;
	}

	public int GetIndexMatTex(final String szMaterialTexture, final String szDefaultTexture, final boolean fAddIfNotExist, final String szErrorIfFailed)
	{
		// split material/texture pair
		String Material, Texture;
		final String[] split = szMaterialTexture.split("-");
		Material = split[0];
		Texture = split.length > 1 ? split[1] : null;
		// texture not given or invalid?
		int iMatTex = 0;
		if (Texture != null)
			if ((iMatTex = GetIndex(Material, Texture, fAddIfNotExist)) != 0)
				return iMatTex;
		if (szDefaultTexture != null)
			if ((iMatTex = GetIndex(Material, szDefaultTexture, fAddIfNotExist)) != 0)
				return iMatTex;
		// return default map entry
		return 1;
	}

	public int GetIndexMatTex(final String szMaterialTexture) {
		return GetIndexMatTex(szMaterialTexture, null, true, null);
	}

	public int GetIndexMatTex(final String name, final String tex) {
		return GetIndexMatTex(name, tex, true, null);
	}
}
