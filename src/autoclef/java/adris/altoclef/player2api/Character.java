package adris.altoclef.player2api;

import java.util.Arrays;

public record Character (String name, String shortName, String greetingInfo, String description, String skinURL, String[] voiceIds) {
    /**
     * Returns a formatted string representation of the Character object.
     *
     * @return A string containing character details.
     */
    @Override
    public String toString() {
        return String.format(
                "Character{name='%s', shortName='%s', greeting='%s', voiceIds=%s}",
                name,
                shortName,
                greetingInfo,
                Arrays.toString(voiceIds)
        );
    }

}