(ns cmr.umm.iso-smap.collection
  "Contains functions for parsing and generating the SMAP ISO dialect."
  (:require [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clj-time.core :as time]
            [clj-time.format :as f]
            [cmr.common.xml :as cx]
            [cmr.umm.iso-smap.core :as core]
            [cmr.umm.collection :as c]
            [cmr.common.xml :as v]
            [cmr.umm.iso-smap.collection.org :as org]
            [cmr.umm.iso-smap.collection.temporal :as t]
            [cmr.umm.iso-smap.collection.spatial :as spatial]
            [cmr.umm.iso-smap.collection.helper :as h])
  (:import cmr.umm.collection.UmmCollection))

(defn- xml-elem-with-title-tag
  "Returns the identification element with the given tag"
  [id-elems tag]
  (h/xml-elem-with-path-value id-elems [:citation :CI_Citation :title :CharacterString] tag))

(defn- xml-elem-with-id-tag
  "Returns the identification element with the given tag"
  [id-elems tag]
  (h/xml-elem-with-path-value id-elems [:citation :CI_Citation :identifier :MD_Identifier
                                        :description :CharacterString] tag))

(defn- xml-elem->Product
  "Returns a UMM Product from a parsed XML structure"
  [product-elem]
  (let [short-name (cx/string-at-path product-elem [:citation :CI_Citation :identifier :MD_Identifier
                                                    :code :CharacterString])
        long-name (cx/string-at-path product-elem [:citation :CI_Citation :title :CharacterString])
        version-id (cx/string-at-path product-elem [:citation :CI_Citation :edition :CharacterString])]
    (c/map->Product {:short-name short-name
                     :long-name long-name
                     :version-id version-id})))

(defn- xml-elem->DataProviderTimestamps
  "Returns a UMM DataProviderTimestamps from a parsed XML structure"
  [id-elems]
  (let [insert-time-elem (xml-elem-with-title-tag id-elems "InsertTime")
        update-time-elem (xml-elem-with-title-tag id-elems "UpdateTime")
        insert-time (cx/datetime-at-path insert-time-elem [:citation :CI_Citation :date :CI_Date :date :DateTime])
        update-time (cx/datetime-at-path update-time-elem [:citation :CI_Citation :date :CI_Date :date :DateTime])]
    (when (or insert-time update-time)
      (c/map->DataProviderTimestamps
        {:insert-time insert-time
         :update-time update-time}))))

(defn- xml-elem->associated-difs
  "Returns associated difs from a parsed XML structure"
  [id-elems]
  ;; There can be no more than one DIF ID for SMAP ISO
  (let [dif-elem (xml-elem-with-title-tag id-elems "DIFID")
        dif-id (cx/string-at-path
                 dif-elem
                 [:citation :CI_Citation :identifier :MD_Identifier :code :CharacterString])]
    (when dif-id [dif-id])))

(defn- xml-elem->Collection
  "Returns a UMM Product from a parsed Collection XML structure"
  [xml-struct]
  (let [id-elems (cx/elements-at-path xml-struct [:seriesMetadata :MI_Metadata :identificationInfo
                                                  :MD_DataIdentification])
        product-elem (xml-elem-with-id-tag id-elems "The ECS Short Name")
        product (xml-elem->Product product-elem)
        data-provider-timestamps (xml-elem->DataProviderTimestamps id-elems)
        dataset-id-elem (xml-elem-with-title-tag id-elems "DataSetId")]
    (c/map->UmmCollection
      {:entry-id (str (:short-name product) "_" (:version-id product))
       :entry-title (cx/string-at-path
                      dataset-id-elem
                      [:aggregationInfo :MD_AggregateInformation :aggregateDataSetIdentifier
                       :MD_Identifier :code :CharacterString])
       :summary (cx/string-at-path product-elem [:abstract :CharacterString])
       :product product
       :data-provider-timestamps data-provider-timestamps
       :temporal (t/xml-elem->Temporal xml-struct)
       :spatial-coverage (spatial/xml-elem->SpatialCoverage xml-struct)
       :organizations (org/xml-elem->Organizations id-elems)
       :associated-difs (xml-elem->associated-difs id-elems)
       })))

(defn parse-collection
  "Parses ISO XML into a UMM Collection record."
  [xml]
  (xml-elem->Collection (x/parse-str xml)))

(def iso-header-attributes
  "The set of attributes that go on the dif root element"
  {:xmlns:gmd "http://www.isotc211.org/2005/gmd"
   :xmlns:gco "http://www.isotc211.org/2005/gco"
   :xmlns:gmi "http://www.isotc211.org/2005/gmi"
   :xmlns:gml "http://www.opengis.net/gml/3.2"
   :xmlns:gmx "http://www.isotc211.org/2005/gmx"
   :xmlns:gsr "http://www.isotc211.org/2005/gsr"
   :xmlns:gss "http://www.isotc211.org/2005/gss"
   :xmlns:gts "http://www.isotc211.org/2005/gts"
   :xmlns:srv "http://www.isotc211.org/2005/srv"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"})

(def iso-charset-element
  "Defines the iso-charset-element"
  (let [iso-code-list-attributes
        {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode"
         :codeListValue "utf8"}]
    (x/element :gmd:characterSet {}
               (x/element :gmd:MD_CharacterSetCode iso-code-list-attributes "utf8"))))

(def iso-hierarchy-level-element
  "Defines the iso-hierarchy-level-element"
  (x/element :gmd:hierarchyLevel {} h/scope-code-element))

(def iso-status-element
  "Defines the iso-status element"
  (x/element
    :gmd:status {}
    (x/element
      :gmd:MD_ProgressCode
      {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#MD_ProgressCode"
       :codeListValue "ongoing"}
      "ongoing")))

(defn- iso-aggregation-info-element
  "Defines the iso-aggregation-info element"
  [dataset-id]
  (x/element
    :gmd:aggregationInfo {}
    (x/element
      :gmd:MD_AggregateInformation {}
      (x/element :gmd:aggregateDataSetIdentifier {}
                 (x/element :gmd:MD_Identifier {}
                            (h/iso-string-element :gmd:code dataset-id)))
      (x/element :gmd:associationType {}
                 (x/element :gmd:DS_AssociationTypeCode
                            {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode"
                             :codeListValue "largerWorkCitation"}
                            "largerWorkCitation"))
      (x/element :gmd:initiativeType {}
                 (x/element :gmd:DS_InitiativeTypeCode
                            {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode"
                             :codeListValue "mission"}
                            "mission")))))

(defn- iso-date-type-element
  "Returns the iso date type element for the given type"
  [type]
  (x/element
    :gmd:dateType {}
    (x/element
      :gmd:CI_DateTypeCode
      {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode"
       :codeListValue type} type)))

(defn- iso-date-element
  "Returns the iso date element based on the given type and date"
  ([type date]
   (iso-date-element type date false))
  ([type date date-only?]
   (x/element :gmd:date {}
              (x/element :gmd:CI_Date {}
                         (x/element :gmd:date {}
                                    (if date-only?
                                      (x/element :gco:Date {} (f/unparse (f/formatters :date) date))
                                      (x/element :gco:DateTime {} (str date))))
                         (iso-date-type-element type)))))

(defn- generate-short-name-element
  "Returns the smap iso short name element"
  [short-name]
  (x/element
    :gmd:identifier {}
    (x/element
      :gmd:MD_Identifier {}
      (h/iso-string-element :gmd:code short-name)
      (h/iso-string-element :gmd:codeSpace "smap.jpl.nasa.gov")
      (h/iso-string-element :gmd:description "The ECS Short Name"))))

(defn- generate-citation-element
  "Returns the citation element with the given title and datetime"
  [title type date]
  (x/element :gmd:citation {}
             (x/element :gmd:CI_Citation {}
                        (h/iso-string-element :gmd:title title)
                        (iso-date-element type date))))

(defn- generate-dataset-id-element
  "Returns the smap iso dataset id element"
  [dataset-id update-time]
  (x/element
    :gmd:identificationInfo {}
    (x/element
      :gmd:MD_DataIdentification {}
      (generate-citation-element "DataSetId" "revision" update-time)
      (h/iso-string-element :gmd:abstract "DataSetId")
      (x/element :gmd:aggregationInfo {}
                 (x/element :gmd:MD_AggregateInformation {}
                            (x/element :gmd:aggregateDataSetIdentifier {}
                                       (x/element :gmd:MD_Identifier {}
                                                  (h/iso-string-element :gmd:code dataset-id)))
                            (x/element :gmd:associationType {})))
      (h/iso-string-element :gmd:language "eng"))))

(defn- generate-datetime-element
  "Returns the smap iso update-time/insert-time element"
  [title date-type datetime]
  (x/element
    :gmd:identificationInfo {}
    (x/element
      :gmd:MD_DataIdentification {}
      (generate-citation-element title date-type datetime)
      (h/iso-string-element :gmd:abstract title)
      (h/iso-string-element :gmd:purpose title)
      (h/iso-string-element :gmd:language "eng"))))

(defn- generate-dif-element
  "Returns the smap iso update-time/insert-time element"
  [dif-id datetime]
  (x/element
    :gmd:identificationInfo {}
    (x/element
      :gmd:MD_DataIdentification {}
      (x/element :gmd:citation {}
                 (x/element :gmd:CI_Citation {}
                            (h/iso-string-element :gmd:title "DIFID")
                            (iso-date-element "revision" datetime)
                            (x/element :gmd:identifier {}
                                       (x/element :gmd:MD_Identifier {}
                                                  (h/iso-string-element :gmd:code dif-id)))))
      (h/iso-string-element :gmd:abstract "DIFID")
      (h/iso-string-element :gmd:purpose "DIFID")
      (h/iso-string-element :gmd:language "eng"))))

(extend-protocol cmr.umm.iso-smap.core/UmmToIsoSmapXml
  UmmCollection
  (umm->iso-smap-xml
    ([collection]
     (cmr.umm.iso-smap.core/umm->iso-smap-xml collection false))
    ([collection indent?]
     (let [{{:keys [short-name long-name version-id]} :product
            dataset-id :entry-title
            {:keys [insert-time update-time]} :data-provider-timestamps
            :keys [organizations temporal spatial-coverage summary associated-difs]} collection
           emit-fn (if indent? x/indent-str x/emit-str)]
       (emit-fn
         (x/element
           :gmd:DS_Series iso-header-attributes
           (x/element :gmd:composedOf {:gco:nilReason "inapplicable"})
           (x/element
             :gmd:seriesMetadata {}
             (x/element
               :gmi:MI_Metadata {}
               (h/iso-string-element :gmd:language "eng")
               iso-charset-element
               iso-hierarchy-level-element
               (x/element :gmd:contact {})
               (x/element :gmd:dateStamp {}
                          (x/element :gco:Date {} (f/unparse (f/formatters :date) update-time)))
               (x/element
                 :gmd:identificationInfo {}
                 (x/element
                   :gmd:MD_DataIdentification {}
                   (x/element
                     :gmd:citation {}
                     (x/element
                       :gmd:CI_Citation {}
                       (h/iso-string-element :gmd:title long-name)
                       ;; This should be the RevisionDate, but we don't really index it
                       ;; and the type for ECHO10 is datetime, for DIF and SMAP ISO is date.
                       ;; We need to work out how to handle it.
                       ;; For now, just use the update time to replace the revision date.
                       (iso-date-element "revision" update-time true)
                       (h/iso-string-element :gmd:edition version-id)
                       (generate-short-name-element short-name)
                       (org/generate-processing-center organizations)))
                   (h/iso-string-element :gmd:abstract summary)
                   (h/iso-string-element :gmd:credit "National Aeronautics and Space Administration (NASA)")
                   iso-status-element
                   (org/generate-archive-center organizations)
                   (iso-aggregation-info-element dataset-id)
                   (h/iso-string-element :gmd:language "eng")
                   (x/element
                     :gmd:extent {}
                     (x/element
                       :gmd:EX_Extent {}
                       (spatial/generate-spatial spatial-coverage)
                       (t/generate-temporal temporal)))))
               (generate-dataset-id-element dataset-id update-time)
               (generate-datetime-element "InsertTime" "creation" insert-time)
               (generate-datetime-element "UpdateTime" "revision" update-time)
               (generate-dif-element (first associated-difs) update-time)))))))))


(defn validate-xml
  "Validates the XML against the ISO schema."
  [xml]
  (v/validate-xml (io/resource "schema/iso_smap/schema.xsd") xml))


