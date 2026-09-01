package ai.instavision.sandbox.ui.settings.device

import ai.instavision.guardian.sdk.data.entity.cluster.AttributeProperty
import ai.instavision.guardian.sdk.data.entity.cluster.DeviceCluster
import ai.instavision.guardian.sdk.data.entity.cluster.RadarProperties
import ai.instavision.guardian.sdk.data.entity.cluster.radarProperties
import ai.instavision.guardian.sdk.data.enums.cluster.ClusterAttributeTypes
import ai.instavision.guardian.sdk.data.enums.cluster.DeviceClusterTypes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Doorbell
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.HdrOn
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Angle a camera reports when its picture is the right way up. */
private const val ROTATION_UPRIGHT = 0

/** Angle a camera reports when its picture is flipped for an upside-down mount. */
private const val ROTATION_FLIPPED = 180

/** How many values a level control offers at most, so its chip row stays one comfortable line. */
private const val LEVEL_CHOICE_LIMIT = 6

/** Minutes in an hour, for turning a schedule attribute back into a time of day. */
private const val MINUTES_PER_HOUR = 60

/** Minutes in a day, which is what a schedule attribute is wrapped onto before it is read. */
private const val MINUTES_PER_DAY = 1440

/** How a schedule time reads once it has been moved into the phone's own timezone. */
private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("hh:mm a")

/** Clip lengths the camera is offered, which is a fixed ladder rather than the attribute's grid. */
private val EVENT_DURATIONS = listOf(10, 30, 60, 120, 180)

/** Which camera-settings screen a cluster-backed control belongs on. */
enum class ClusterSection {
  /** The settings hub itself, for the controls that act on the camera body. */
  General,

  /** The lamps the camera carries, grouped on the hub beneath the general controls. */
  Light,

  /** The thresholds of the camera's own temperature and humidity sensors. */
  Sensors,

  /** The events and detection sub-screen. */
  Detection,

  /** The live view sub-screen. */
  LiveView,

  /** The audio sub-screen. */
  Audio,
}

/**
 * How a setting renders when the attribute's own property block would lead the generic rule
 * astray. Every one of these mirrors a place the production app overrides the shape by hand.
 */
enum class ClusterOverride {
  /** No override: the property block decides, which is what nearly every attribute wants. */
  None,

  /** Numeric on the wire but on-or-off to a person, folded onto two values. */
  Flip,

  /** Numeric on the wire but only offered as a fixed ladder of values. */
  Steps,

  /** The radar sector map, offered as the three preset depths rather than as raw sectors. */
  Radar,
}

/**
 * One capability the Guardian cluster model gives an id to, paired with the wording, glyph and
 * placement this sample renders it with. Listing a setting here does not put it on screen: a
 * camera only gets the row when its own cluster reports that cluster id and attribute id.
 *
 * The catalogue deliberately leaves out the attributes the production app reads but never renders
 * as a control — the edge-AI flags and the alert switches it uses purely to decide which cloud
 * toggle to show, the pan and tilt positions its live view drives, and the hardware, signal and
 * location blocks it reads for other purposes. The event-scheduling and activity-zone attributes
 * are left out too, because both are cards of their own with a switch that reveals the rest.
 */
@Suppress("LongParameterList")
enum class ClusterSetting(
  /** Cluster the attribute lives in, which is the first half of the address of a write. */
  val cluster: DeviceClusterTypes,
  /** Attribute holding the value, which is the second half of the address of a write. */
  val attribute: ClusterAttributeTypes,
  /** Screen the control belongs on. */
  val section: ClusterSection,
  /** Label of the row. */
  val title: String,
  /** Supporting line beneath the label, left out when the label says enough. */
  val description: String? = null,
  /** Accent glyph at the head of the row. */
  val icon: ImageVector? = null,
  /** Suffix appended to a numeric value, for example a degree sign or a percentage. */
  val unit: String = "",
  /** Shape correction to apply, when the property block alone would get the control wrong. */
  val override: ClusterOverride = ClusterOverride.None,
  /** Value written when a numeric switch is turned on. */
  val switchOn: Int? = null,
  /** Value written when a numeric switch is turned off. */
  val switchOff: Int? = null,
  /** The only values a [ClusterOverride.Steps] control offers. */
  val levels: List<Int> = emptyList(),
) {
  /** Covers the lens, which stops the camera streaming and recording entirely. */
  PrivacyMode(
    cluster = DeviceClusterTypes.PrivacyMode,
    attribute = ClusterAttributeTypes.PrivacyMode,
    section = ClusterSection.General,
    title = "Privacy mode",
    description = "Stops recording and disables the lens",
    icon = Icons.Outlined.VisibilityOff,
  ),

  /** Whether a motorised camera follows what it sees. */
  MotionTracking(
    cluster = DeviceClusterTypes.HumanTracking,
    attribute = ClusterAttributeTypes.MotionTracking,
    section = ClusterSection.General,
    title = "Motion tracking",
    description = "The camera turns to follow what it sees",
    icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
  ),

  /** The small LED on the camera body. */
  StatusLight(
    cluster = DeviceClusterTypes.StatusLight,
    attribute = ClusterAttributeTypes.StatusLight,
    section = ClusterSection.General,
    title = "Status light",
    description = "The small LED on the camera body",
    icon = Icons.Outlined.Lightbulb,
  ),

  /** Whether the time is burned into the corner of the picture. */
  ShowTimestamp(
    cluster = DeviceClusterTypes.OSDSettings,
    attribute = ClusterAttributeTypes.OsdTime,
    section = ClusterSection.General,
    title = "Show timestamp",
    description = "Burns the camera's clock into the corner of the picture",
    icon = Icons.Outlined.Schedule,
  ),

  /** Continuous recording onto the card in the camera. */
  SdCardRecording(
    cluster = DeviceClusterTypes.SDCardSettings,
    attribute = ClusterAttributeTypes.CVRMode,
    section = ClusterSection.General,
    title = "Record to SD card",
    description = "Keep continuous video on the card",
    icon = Icons.Outlined.SdStorage,
  ),

  /** Cuts the cellular connection of a 4G camera to preserve its data allowance. */
  DataOff(
    cluster = DeviceClusterTypes.FourG,
    attribute = ClusterAttributeTypes.FourGDataOff,
    section = ClusterSection.General,
    title = "Data off",
    description = "Pause live view and uploads to save data",
    icon = Icons.Outlined.SignalCellularAlt,
  ),

  /** Streams a 4G camera at a lower resolution to preserve its data allowance. */
  LowDataMode(
    cluster = DeviceClusterTypes.FourG,
    attribute = ClusterAttributeTypes.FourGLowDataMode,
    section = ClusterSection.General,
    title = "Low data mode",
    description = "Stream at a lower resolution",
    icon = Icons.Outlined.DataUsage,
  ),

  /** What a doorbell plays indoors when its button is pressed. */
  Chime(
    cluster = DeviceClusterTypes.ChimeSettings,
    attribute = ClusterAttributeTypes.ChimeSettings,
    section = ClusterSection.General,
    title = "Chime",
    description = "What plays indoors when the button is pressed",
    icon = Icons.Outlined.Doorbell,
  ),

  /** When the camera switches to infrared. */
  NightVision(
    cluster = DeviceClusterTypes.NightVision,
    attribute = ClusterAttributeTypes.NightVisionMode,
    section = ClusterSection.Light,
    title = "Night vision",
    icon = Icons.Outlined.Nightlight,
  ),

  /** The bright lamp some cameras carry, switched by hand. */
  Spotlight(
    cluster = DeviceClusterTypes.CameraLight,
    attribute = ClusterAttributeTypes.CameraLightLight,
    section = ClusterSection.Light,
    title = "Spotlight",
    description = "The bright lamp on the camera body",
    icon = Icons.Outlined.Highlight,
  ),

  /** When the spotlight comes on by itself. */
  SpotlightMode(
    cluster = DeviceClusterTypes.CameraLight,
    attribute = ClusterAttributeTypes.CameraLightLightMode,
    section = ClusterSection.Light,
    title = "Spotlight mode",
    icon = Icons.Outlined.Tune,
  ),

  /** When the flood light comes on. */
  FloodLight(
    cluster = DeviceClusterTypes.FloodLight,
    attribute = ClusterAttributeTypes.FloodLightMode,
    section = ClusterSection.Light,
    title = "Flood light",
    icon = Icons.Outlined.WbSunny,
  ),

  /** When the guide lamp that lights the approach comes on. */
  GuideLamp(
    cluster = DeviceClusterTypes.GuideLamp,
    attribute = ClusterAttributeTypes.GuideLampMode,
    section = ClusterSection.Light,
    title = "Guide light",
    icon = Icons.Outlined.Nightlight,
  ),

  /** Whether the light flashes along with the siren. */
  AlarmLight(
    cluster = DeviceClusterTypes.AlarmLight,
    attribute = ClusterAttributeTypes.AlarmLightMode,
    section = ClusterSection.Light,
    title = "Siren light",
    description = "Flashes whenever the siren sounds",
    icon = Icons.Outlined.FlashOn,
  ),

  /** The soft lamp on a nursery camera. */
  NightLamp(
    cluster = DeviceClusterTypes.NightLampSettings,
    attribute = ClusterAttributeTypes.NightLampEnabled,
    section = ClusterSection.Light,
    title = "Night lamp",
    icon = Icons.Outlined.Bedtime,
  ),

  /** How bright the night lamp burns. */
  NightLampBrightness(
    cluster = DeviceClusterTypes.NightLampSettings,
    attribute = ClusterAttributeTypes.NightLampBrightness,
    section = ClusterSection.Light,
    title = "Night lamp brightness",
    icon = Icons.Outlined.Brightness6,
    unit = "%",
  ),

  /** Reading below which a temperature alert is raised. */
  LowTemperature(
    cluster = DeviceClusterTypes.TemperatureSettings,
    attribute = ClusterAttributeTypes.LowTemperatureThreshold,
    section = ClusterSection.Sensors,
    title = "Low temperature",
    description = "Alert below this reading",
    icon = Icons.Outlined.Thermostat,
    unit = "°",
  ),

  /** Reading above which a temperature alert is raised. */
  HighTemperature(
    cluster = DeviceClusterTypes.TemperatureSettings,
    attribute = ClusterAttributeTypes.HighTemperatureThreshold,
    section = ClusterSection.Sensors,
    title = "High temperature",
    description = "Alert above this reading",
    icon = Icons.Outlined.Thermostat,
    unit = "°",
  ),

  /** Reading below which a humidity alert is raised. */
  LowHumidity(
    cluster = DeviceClusterTypes.HumiditySettings,
    attribute = ClusterAttributeTypes.LowHumidityThreshold,
    section = ClusterSection.Sensors,
    title = "Low humidity",
    description = "Alert below this reading",
    icon = Icons.Outlined.WaterDrop,
    unit = "%",
  ),

  /** Reading above which a humidity alert is raised. */
  HighHumidity(
    cluster = DeviceClusterTypes.HumiditySettings,
    attribute = ClusterAttributeTypes.HighHumidityThreshold,
    section = ClusterSection.Sensors,
    title = "High humidity",
    description = "Alert above this reading",
    icon = Icons.Outlined.WaterDrop,
    unit = "%",
  ),

  /** How much of a change in the picture counts as movement. */
  DetectionSensitivity(
    cluster = DeviceClusterTypes.MotionSensorSensitivityLevel,
    attribute = ClusterAttributeTypes.MotionSensitivityLevel,
    section = ClusterSection.Detection,
    title = "Detection sensitivity",
    icon = Icons.Outlined.Tune,
  ),

  /** How readily the passive infrared sensor decides something moved. */
  MotionSensorSensitivity(
    cluster = DeviceClusterTypes.PIRSensorSensitivityLevel,
    attribute = ClusterAttributeTypes.PIRSensitivityLevel,
    section = ClusterSection.Detection,
    title = "Motion sensor sensitivity",
    icon = Icons.Outlined.Tune,
  ),

  /** How long the camera keeps quiet after an event before it raises another. */
  MotionCooldown(
    cluster = DeviceClusterTypes.MotionDetection,
    attribute = ClusterAttributeTypes.MotionDetectionCoolDownPeriod,
    section = ClusterSection.Detection,
    title = "Event cooldown",
    description = "How long the camera waits before raising another event",
    icon = Icons.Outlined.Timer,
  ),

  /** How long a clip the camera records once something sets it off. */
  EventLength(
    cluster = DeviceClusterTypes.EventDuration,
    attribute = ClusterAttributeTypes.EventDuration,
    section = ClusterSection.Detection,
    title = "Event length",
    icon = Icons.Outlined.Timer,
    unit = "s",
    override = ClusterOverride.Steps,
    levels = EVENT_DURATIONS,
  ),

  /** How far out a radar camera watches, as the presets the production app offers. */
  RadarDetection(
    cluster = DeviceClusterTypes.RadarDetection,
    attribute = ClusterAttributeTypes.RadarDetection,
    section = ClusterSection.Detection,
    title = "Radar detection zone",
    description = "How far from the camera movement is picked up",
    icon = Icons.Outlined.Radar,
    override = ClusterOverride.Radar,
  ),

  /** Evens out the very bright and very dark parts of a frame. */
  Hdr(
    cluster = DeviceClusterTypes.HDREnable,
    attribute = ClusterAttributeTypes.HDREnable,
    section = ClusterSection.LiveView,
    title = "HDR",
    description = "Even out the bright and dark parts of the frame",
    icon = Icons.Outlined.HdrOn,
  ),

  /** Flips the picture for a camera mounted upside down. */
  ImageRotation(
    cluster = DeviceClusterTypes.RotationalAngle,
    attribute = ClusterAttributeTypes.RotationalAngle,
    section = ClusterSection.LiveView,
    title = "Rotate image 180°",
    description = "For a camera mounted upside down",
    icon = Icons.Outlined.ScreenRotation,
    override = ClusterOverride.Flip,
    switchOn = ROTATION_FLIPPED,
    switchOff = ROTATION_UPRIGHT,
  ),

  /** Whether the logo is burned into the corner of the picture. */
  ShowLogo(
    cluster = DeviceClusterTypes.OSDSettings,
    attribute = ClusterAttributeTypes.OsdLogo,
    section = ClusterSection.LiveView,
    title = "Show logo",
    icon = Icons.Outlined.Image,
  ),

  /** How much data the camera is allowed to spend on the stream. */
  BitRate(
    cluster = DeviceClusterTypes.BitRate,
    attribute = ClusterAttributeTypes.BitRate,
    section = ClusterSection.LiveView,
    title = "Bit rate",
    description = "How much data the stream is allowed",
    icon = Icons.Outlined.Speed,
  ),

  /** Whether sound is recorded alongside the picture. */
  Microphone(
    cluster = DeviceClusterTypes.AudioSettings,
    attribute = ClusterAttributeTypes.MicrophoneEnabled,
    section = ClusterSection.Audio,
    title = "Microphone",
    description = "Records sound alongside the picture",
    icon = Icons.Outlined.Mic,
  ),

  /** How loud the speaker plays. */
  Volume(
    cluster = DeviceClusterTypes.AudioSettings,
    attribute = ClusterAttributeTypes.SpeakerVolume,
    section = ClusterSection.Audio,
    title = "Volume",
    icon = Icons.AutoMirrored.Outlined.VolumeUp,
  ),
  ;

  /** The value to send for a switch, folding a numeric switch back onto its two wire values. */
  fun switchValue(checked: Boolean): Any = when {
    switchOn == null || switchOff == null -> checked
    checked -> switchOn
    else -> switchOff
  }
}

/** The three depths a radar camera is offered, matching the presets the production app writes. */
private enum class RadarZone(
  /** Wire name of the preset, which is what the camera reports back as its level. */
  val level: String,
  /** Sectors the preset lights up, nearest to the camera first. */
  val sectors: List<Int>,
) {
  /** Only the band closest to the camera. */
  Near(level = "Near", sectors = listOf(0, 1, 2, 3)),

  /** The near band and the one beyond it. */
  Medium(level = "Medium", sectors = listOf(4, 5, 6, 7, 0, 1, 2, 3)),

  /** Everything the radar reaches. */
  Far(level = "Far", sectors = listOf(8, 9, 10, 11, 4, 5, 6, 7, 0, 1, 2, 3)),
}

/** One of the fixed values an attribute advertises, as its wire value and its display label. */
data class ClusterOption(
  /** Identity of the option, compared against what the camera currently reports. */
  val key: String,
  /** What the user reads, falling back to the wire value when the camera names no label. */
  val label: String,
  /** What is written back, which is the attribute's own enum string for all but the radar map. */
  val value: Any = key,
)

/**
 * A control a camera reports through its cluster, already resolved to the shape it renders as.
 * The shape comes from the attribute's own property block unless the catalogue overrides it.
 */
sealed interface ClusterControl {
  /** Catalogue entry this control was built from, carrying its wording and its address. */
  val setting: ClusterSetting

  /** An attribute that is on or off. */
  data class Switch(
    override val setting: ClusterSetting,
    /** Whether the camera reports the attribute as on. */
    val checked: Boolean,
  ) : ClusterControl

  /** An attribute offered as a fixed set of values, whether the camera's own or a preset ladder. */
  data class Choice(
    override val setting: ClusterSetting,
    /** Values on offer, in the order they were listed. */
    val options: List<ClusterOption>,
    /** Option key the camera currently holds, which need not be one of [options]. */
    val selected: String,
  ) : ClusterControl

  /** A number, offered as the handful of values that fit on one line. */
  data class Level(
    override val setting: ClusterSetting,
    /** Value the camera currently holds. */
    val value: Int,
    /** Values on offer, always including [value] so the selection stays visible. */
    val choices: List<Int>,
  ) : ClusterControl

  /** An attribute the sample can show but has no way to edit. */
  data class Readout(
    override val setting: ClusterSetting,
    /** Value the camera currently holds, already formatted for reading. */
    val value: String,
  ) : ClusterControl
}

/**
 * Every control this camera actually advertises, in catalogue order. A camera that reports no
 * clusters produces an empty list, which is what the screens fall back on.
 */
fun DeviceCluster.controls(): List<ClusterControl> =
  ClusterSetting.entries.mapNotNull { setting -> setting.controlIn(deviceCluster = this) }

/** The label to put in a row's value slot for the option the camera currently holds. */
fun ClusterControl.Choice.selectedLabel(): String =
  options.firstOrNull { option -> option.key == selected }?.label ?: selected

/** Builds this setting's control, or null when the camera does not report the attribute at all. */
private fun ClusterSetting.controlIn(deviceCluster: DeviceCluster): ClusterControl? {
  val property = deviceCluster.clusters
    .firstOrNull { reported -> reported.id == cluster.id }
    ?.attributes
    ?.firstOrNull { reported -> reported.id == attribute.id }
    ?.property
    ?: return null
  return control(property = property, deviceCluster = deviceCluster)
}

/**
 * Decides what a reported attribute renders as, applying the catalogue's override first so the
 * handful of attributes the production app shapes by hand end up the same way here.
 */
private fun ClusterSetting.control(
  property: AttributeProperty,
  deviceCluster: DeviceCluster,
): ClusterControl {
  val number = property.value.toString().toFloatOrNull()?.toInt()
  return when (override) {
    ClusterOverride.Flip -> ClusterControl.Switch(setting = this, checked = number == switchOn)

    ClusterOverride.Steps -> ClusterControl.Level(
      setting = this,
      value = number ?: levels.firstOrNull() ?: 0,
      choices = (levels + listOfNotNull(number)).distinct().sorted(),
    )

    ClusterOverride.Radar -> ClusterControl.Choice(
      setting = this,
      options = RadarZone.entries.map { zone ->
        ClusterOption(
          key = zone.level,
          label = zone.level,
          value = RadarProperties(level = zone.level, sectors = zone.sectors),
        )
      },
      selected = deviceCluster.radarProperties()?.level.orEmpty(),
    )

    ClusterOverride.None -> advertised(property = property, number = number)
  }
}

/**
 * The shape the attribute's own property block asks for: a boolean is a switch, an advertised set
 * of labels is a picker, a bounded number is a level, and anything else can only be shown.
 */
private fun ClusterSetting.advertised(property: AttributeProperty, number: Int?): ClusterControl {
  val options = property.labels.orEmpty().map { label ->
    ClusterOption(key = label.enum, label = label.label ?: label.enum)
  }
  return when {
    property.value is Boolean ->
      ClusterControl.Switch(setting = this, checked = property.value == true)

    options.isNotEmpty() -> ClusterControl.Choice(
      setting = this,
      options = options,
      selected = property.value.toString(),
    )

    number != null && property.max > property.min -> ClusterControl.Level(
      setting = this,
      value = number,
      choices = levelChoices(
        value = number,
        min = property.min,
        max = property.max,
        step = property.step,
      ),
    )

    else -> ClusterControl.Readout(setting = this, value = property.value.toString())
  }
}

/**
 * The values a level control offers as chips: its ends, evenly spaced points on the camera's own
 * step grid between them, and wherever it currently sits so the selection is always visible.
 */
private fun levelChoices(value: Int, min: Int, max: Int, step: Int): List<Int> {
  val stride = step.coerceAtLeast(1)
  val stops = ((max - min) / stride).coerceAtLeast(1)
  val spacing = ((stops + LEVEL_CHOICE_LIMIT - 1) / LEVEL_CHOICE_LIMIT).coerceAtLeast(1)
  val offered = (0..stops step spacing).map { index -> min + index * stride }
  return (offered + max + value).distinct().filter { choice -> choice in min..max }.sorted()
}

/**
 * Turns a schedule attribute into the time of day it names. The camera counts minutes from
 * midnight, and the count is wrapped onto a single day first so a camera that reports one past
 * the end of the day still gives a time rather than throwing.
 */
fun Int.minutesToTime(): LocalTime {
  val wrapped = Math.floorMod(this, MINUTES_PER_DAY)
  return LocalTime.of(wrapped / MINUTES_PER_HOUR, wrapped % MINUTES_PER_HOUR)
}

/**
 * Reads a time the camera keeps in UTC as the wall time the phone is on. Today's date carries the
 * conversion so the offset in force right now — daylight saving included — is the one applied.
 */
fun LocalTime.toLocalZone(): LocalTime = atDate(LocalDate.now())
  .atZone(ZoneOffset.UTC)
  .withZoneSameInstant(ZoneId.systemDefault())
  .toLocalTime()

/** The inverse of [toLocalZone]: a time the user picked, as the camera's own UTC clock reads it. */
fun LocalTime.toUtcZone(): LocalTime = atDate(LocalDate.now())
  .atZone(ZoneId.systemDefault())
  .withZoneSameInstant(ZoneOffset.UTC)
  .toLocalTime()

/** A picked time as the minutes past midnight UTC a cluster schedule attribute is written in. */
fun LocalTime.toUtcMinutes(): Int = toUtcZone().let { it.hour * MINUTES_PER_HOUR + it.minute }

/** A schedule time as the row shows it, or an em dash while the camera has not reported one. */
fun LocalTime?.clockLabel(): String = this?.format(CLOCK_FORMAT) ?: "—"
