;; WARNING: This file was generated from umm-var-json-schema.json. Do not manually modify.
(ns cmr.umm-spec.models.umm-variable-models
   "Defines UMM-Var clojure records."
 (:require [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

(defrecord UMM-Var
  [
   ;; Valid ranges of variable data values.
   ValidRanges

   ;; A variable consists of one or more dimensions. An example of a dimension name is 'XDim'. An
   ;; example of a dimension size is '1200'. Variables are rarely one dimensional.
   Dimensions

   ;; The scale is the numerical factor by which all values in the stored data field are multiplied
   ;; in order to obtain the original values. May be used together with Offset. An example of a
   ;; scale factor is '0.002'
   Scale

   ;; The offset is the value which is either added to or subtracted from all values in the stored
   ;; data field in order to obtain the original values. May be used together with Scale. An example
   ;; of an offset is '0.49'.
   Offset

   ;; The set information of a variable. The variable is grouped within a set. The set is defined by
   ;; the name, type, size and index. For example, Name: 'Data_Fields', Type: 'General', Size: '15',
   ;; Index: '7' for the case of the variable named 'LST_Day_1km'.
   Sets

   ;; The units associated with a variable.
   Units

   ;; The sampling information of a variable.
   SamplingIdentifiers

   ;; The fill value of the variable in the data file. It is generally a value which falls outside
   ;; the valid range. For example, if the valid range is '0, 360', the fill value may be '-1'. The
   ;; fill value type is data provider-defined. For example, 'Out of Valid Range'.
   FillValues

   ;; The definition of the variable.
   Definition

   ;; The acquisition source name such as an instrument short name or simulation name to which the
   ;; variable belongs. The acquisition source name is used to help determine uniqueness along with
   ;; Name, Units, and Dimensions in the metadata content for each variable metadata record. For
   ;; more information please see https://wiki.earthdata.nasa.gov/display/DUTRAIN/UMM-VAR+uniqueness
   AcquisitionSourceName

   ;; The characteristics of a variable. The elements of this section apply to a Variable.
   Characteristics

   ;; Controlled Science Keywords describing the measurements/variables. The controlled vocabulary
   ;; for Science Keywords is maintained in the Keyword Management System (KMS).
   ScienceKeywords

   ;; The name of a variable.
   Name

   ;; Specify basic type of a variable. These types can be either: SCIENCE_VARIABLE,
   ;; QUALITY_VARIABLE, ANCILLARY_VARIABLE, OTHER.
   VariableType

   ;; Specifies the sub type of a variable. These types can be either: SCIENCE_SCALAR,
   ;; SCIENCE_VECTOR, SCIENCE_ARRAY, SCIENCE_EVENTFLAG, OTHER.
   VariableSubType

   ;; The size estimation information of a variable.
   SizeEstimation

   ;; The alias for the name of a variable.
   Alias

   ;; The measurement information of a variable.
   MeasurementIdentifiers

   ;; The expanded or long name related to the variable Name.
   LongName

   ;; Specify data type of a variable. These types can be either: uint8, uint16, etc.
   DataType
  ])
(record-pretty-printer/enable-record-pretty-printing UMM-Var)

;; The fill value, fill value type and fill value description of the variable in the data file. The
;; fill value is generally a value which falls outside the valid range. For example, if the valid
;; range is '0, 360', the fill value may be '-1'. The elements of this section apply to the fill
;; value of a variable.
(defrecord FillValueType
  [
   ;; The fill value of the variable in the data file.
   Value

   ;; Type of the fill value of the variable in the data file.
   Type

   ;; Description of the fill value of the variable in the data file.
   Description
  ])
(record-pretty-printer/enable-record-pretty-printing FillValueType)

;; The elements of this section apply to a measurement.
(defrecord SamplingIdentifierType
  [
   ;; The name of the sampling method used for the measurement. For example, 'radiometric detection
   ;; within the visible and infra-red ranges of the electromagnetic spectrum.
   SamplingMethod

   ;; The measurement conditions of the variable. For example, 'Sampled Particle Size Range: 90 -
   ;; 600 nm'.
   MeasurementConditions

   ;; The reporting conditions of the variable. The conditions over which the measurements of the
   ;; variable are valid. For example, 'STP: 1013 mb and 273 K'.
   ReportingConditions
  ])
(record-pretty-printer/enable-record-pretty-printing SamplingIdentifierType)

;; Enables specification of Earth science keywords related to the collection. The Earth Science
;; keywords are chosen from a controlled keyword hierarchy maintained in the Keyword Management
;; System (KMS). The valid values can be found at the KMS website:
;; https://gcmdservices.gsfc.nasa.gov/kms/concepts/concept_scheme/sciencekeywords?format=csv.
(defrecord ScienceKeywordType
  [
   Category

   Topic

   Term

   VariableLevel1

   VariableLevel2

   VariableLevel3

   DetailedVariable
  ])
(record-pretty-printer/enable-record-pretty-printing ScienceKeywordType)

;; The elements of this section apply to a measurement name. The measurement name is structured
;; according to the form defined by Scott Peckham. This is: <object>_<quantity>.
(defrecord MeasurementNameType
  [
   ;; This element allows authors to identify the object part of the measurement. For example:
   ;; land_subsurface_water-sat, land_surface, land_surface_air, land_surface_air_flow,
   ;; land_surface_air_heat, specific_humidity, radiative_flux, q.
   MeasurementObject

   ;; This element allows authors to identify the quantity part of the measurement. For example:
   ;; zone-top, incoming-latent, incoming-sensible, standard_error, detection_minimum,
   ;; at_top_of_atmosphere_model, at_sea_level, error_limit, detection_limit
   MeasurementQuantity
  ])
(record-pretty-printer/enable-record-pretty-printing MeasurementNameType)

;; Valid range data value of a variable: minimum and maximum values. For example, '-100, 5000'.
(defrecord ValidRangeType
  [
   ;; Minimum data value of a variable. For example, '-100'.
   Min

   ;; Maximum data value of a variable. For example, '5000'.
   Max

   ;; This element can be used to specify a code system identifier meaning. For example, 'Open
   ;; Shrubland' corresponds to '7'.
   CodeSystemIdentifierMeaning

   ;; The code system identifier value is the textual or numerical value assigned to each meaning.
   CodeSystemIdentifierValue
  ])
(record-pretty-printer/enable-record-pretty-printing ValidRangeType)

;; The elements of this section apply to variable size estimation.
(defrecord SizeEstimationType
  [
   ;; This element contains the average size for the sampled granules in bytes.
   AverageSizeOfGranulesSampled

   ;; This element is a list of 1 or more average compression rate(s) as a ratio for the granule in
   ;; the specified format. The size estimation service takes this information so that it can
   ;; calculate the approximate downloadable size for the variable.
   AverageCompressionInformation
  ])
(record-pretty-printer/enable-record-pretty-printing SizeEstimationType)

;; The elements of this section apply to a measurement.
(defrecord MeasurementIdentifierType
  [
   ;; This element allows authors to provide community sourced words or phrases to further describe
   ;; the variable data.
   MeasurementName

   ;; This element allows authors to identify the source of the measurements.
   MeasurementSource
  ])
(record-pretty-printer/enable-record-pretty-printing MeasurementIdentifierType)

;; A variable consists of one or more dimensions. An example of a dimension name is 'XDim'. An
;; example of a dimension size is '1200'. Variables are rarely one dimensional.
(defrecord DimensionType
  [
   ;; The name of the dimension of the variable represented in the data field. For example, 'XDim.
   Name

   ;; The size of the dimension of the variable represented in the data field. For example, '1200'.
   Size

   ;; The type of the dimension of the variable represented in the data field. For example, if the
   ;; dimension has a special meaning, i.e., a latitude, longitude, pressure, height (or depth) or
   ;; time, then the type should be set to either 'LATITUDE_DIMENSION', 'LONGITUDE_DIMENSION',
   ;; 'PRESSURE_DIMENSION', 'HEIGHT_DIMENSION', 'DEPTH_DIMENSION' or 'TIME_DIMENSION', otherwise it
   ;; should be set to 'OTHER'.
   Type
  ])
(record-pretty-printer/enable-record-pretty-printing DimensionType)

;; This type defines the container for a specific rate as a ratio for the granule with the
;; accompanying file format.
(defrecord AverageCompressionInformationType
  [
   ;; This element contains the average compression rate as a ratio for the granule for the
   ;; specified format.
   Rate

   ;; This element contains the file format that the size estimation service supports for the given
   ;; rate as a ratio for the granule to be able to predict the size of the downloadable product.
   Format
  ])
(record-pretty-printer/enable-record-pretty-printing AverageCompressionInformationType)

;; The index ranges consist of a LatRange and an LonRange.
(defrecord IndexRangesType
  [
   ;; The LatRange consists of an index range for latitude.
   LatRange

   ;; The LonRange consists of an index range for longitude.
   LonRange
  ])
(record-pretty-printer/enable-record-pretty-printing IndexRangesType)

;; The elements of this section apply to a variable.
(defrecord CharacteristicsType
  [
   ;; Describes the index ranges of a variable, which consist of a LatRange and an LonRange. Each
   ;; field consists of an index range.
   IndexRanges

   ;; The full path to the variable within the Granule structure. For example,
   ;; '/MODIS_Grid_Daily_1km_LST/Data_Fields'.
   GroupPath
  ])
(record-pretty-printer/enable-record-pretty-printing CharacteristicsType)

;; The elements of this section apply to variable sets.
(defrecord SetType
  [
   ;; This element enables specification of set name. For example, 'Data_Fields'.
   Name

   ;; This element enables specification of set type. For example, if the variables have been
   ;; grouped together based on a particular theme, such as wavelength, then the type should be set
   ;; to that theme, otherwise it should be set to 'General'.
   Type

   ;; This element specifies the number of variables in the set. For example, if the number of
   ;; variables in the set is fifteen, the size should be set to '15'.
   Size

   ;; This element specifies the index value within the set for this variable, For example, if this
   ;; varible is the third variable in the set, the index value should be set to '3'.
   Index
  ])
(record-pretty-printer/enable-record-pretty-printing SetType)

;; The coordinates consist of a latitude and longitude.
(defrecord CoordinatesType
  [
   ;; The latitude of the point.
   Lat

   ;; The longitude of the point.
   Lon
  ])
(record-pretty-printer/enable-record-pretty-printing CoordinatesType)