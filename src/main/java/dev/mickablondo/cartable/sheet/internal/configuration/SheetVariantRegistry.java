package dev.mickablondo.cartable.sheet.internal.configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.sheet.SheetTypeOption;
import org.springframework.stereotype.Component;

/**
 * Correspondance explicite entre matière et types proposés à l'utilisateur.
 */
@Component
public class SheetVariantRegistry {

	private static final String GENERIC_TYPE = "GENERIC";
	private static final SheetVariantDefinition GENERIC_VARIANT =
		new SheetVariantDefinition(GENERIC_TYPE, "Fiche générique", 1, "classpath:prompts/sheet-generation-generic.st");

	private final Map<SheetVariantKey, SheetVariantDefinition> variants;

	public SheetVariantRegistry() {
		Map<SheetVariantKey, SheetVariantDefinition> configured = new LinkedHashMap<>();
		for (Subject subject : Subject.values()) {
			configured.put(new SheetVariantKey(subject, GENERIC_TYPE), GENERIC_VARIANT);
		}
		this.variants = Map.copyOf(configured);
	}

	public SheetVariantDefinition require(Subject subject, String sheetType) {
		SheetVariantDefinition variant = variants.get(new SheetVariantKey(subject, sheetType));
		if (variant == null) {
			throw new IllegalArgumentException("Ce type de fiche n'est pas disponible pour cette matière.");
		}
		return variant;
	}

	public List<SheetTypeOption> availableTypes() {
		return variants.values().stream()
			.distinct()
			.map(variant -> new SheetTypeOption(variant.sheetType(), variant.label()))
			.toList();
	}

	public String defaultType(Subject subject) {
		return require(subject, GENERIC_TYPE).sheetType();
	}

}
