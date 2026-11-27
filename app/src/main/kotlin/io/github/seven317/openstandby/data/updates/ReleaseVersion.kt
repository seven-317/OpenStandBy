package io.github.seven317.openstandby.data.updates

@ConsistentCopyVisibility
internal data class ReleaseVersion private constructor(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("""[vV]?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?""")

        fun parse(value: String?): ReleaseVersion? {
            val match = value?.let(pattern::matchEntire) ?: return null
            return ReleaseVersion(
                match.groupValues[1].toIntOrNull() ?: return null,
                match.groupValues[2].toIntOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null,
            )
        }
    }
}
