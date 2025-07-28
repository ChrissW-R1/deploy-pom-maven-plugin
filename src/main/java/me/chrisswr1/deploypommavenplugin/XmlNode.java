package me.chrisswr1.deploypommavenplugin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import proguard.annotation.KeepName;

@NoArgsConstructor
@AllArgsConstructor
public class XmlNode {
	@Getter
	@Setter
	@KeepName
	private @Nullable String xpath = "";
	@Getter
	@Setter
	@KeepName
	private boolean overwrite = false;
	@Getter
	@Setter
	@KeepName
	private boolean copyFromEffective = false;
	@Getter
	@Setter
	@KeepName
	private boolean resolveProperties = false;

	public XmlNode(
		final @Nullable String xpath
	) {
		if (xpath != null) {
			this.xpath = xpath;
		}
	}

	public XmlNode(
		final @Nullable String xpath,
		final boolean copyFromEffective
	) {
		this(xpath);
		this.copyFromEffective = copyFromEffective;
	}
}
