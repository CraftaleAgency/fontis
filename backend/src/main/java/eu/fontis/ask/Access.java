package eu.fontis.ask;

import java.util.List;

/**
 * The documents a request may search.
 *
 * @param all every document (administrators)
 * @param documents ids of the documents allowed when {@code all} is false
 */
public record Access(boolean all, List<String> documents) {

	public static final Access EVERYTHING = new Access(true, List.of());

	public static Access only(List<String> documents) {
		return new Access(false, List.copyOf(documents));
	}

}
