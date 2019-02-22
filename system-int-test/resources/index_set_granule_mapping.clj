{:entry-title.lowercase-doc-values {:doc_values true,
                                    :index "not_analyzed",
                                    :type "string"},
 :start-coordinate-1-doc-values {:doc_values true,
                                 :type "double"},
 :cloud-cover {:store "yes", :type "float"},
 :end-coordinate-1 {:type "double"},
 :mbr-crosses-antimeridian {:type "boolean",
                            :fielddata {:loading "eager"}},
 :granule-ur {:index "not_analyzed",
              :store "yes",
              :type "string"},
 :sensor-sn.lowercase {:index "not_analyzed",
                       :type "string"},
 :short-name.lowercase {:index "not_analyzed",
                        :type "string"},
 :orbit-end-clat-doc-values {:doc_values true,
                             :type "double"},
 :end-direction {:index "not_analyzed",
                 :store "yes",
                 :type "string"},
 :producer-gran-id.lowercase2 {:doc_values true,
                               :index "not_analyzed",
                               :type "string"},
 :lr-south {:type "float"},
 :short-name.lowercase-doc-values {:doc_values true,
                                   :index "not_analyzed",
                                   :type "string"},
 :mbr-south-doc-values {:doc_values true, :type "float"},
 :start-date-doc-values {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                         :doc_values true,
                         :store "yes",
                         :type "date"},
 :native-id.lowercase {:doc_values true,
                       :index "not_analyzed",
                       :type "string"},
 :concept-seq-id {:type "integer"},
 :orbit-start-clat-doc-values {:doc_values true,
                               :type "double"},
 :ords-info {:index "no", :store "yes", :type "integer"},
 :ords {:index "no", :store "yes", :type "integer"},
 :provider-id {:index "not_analyzed",
               :store "yes",
               :type "string"},
 :readable-granule-name-sort2 {:doc_values true,
                               :index "not_analyzed",
                               :type "string"},
 :mbr-north-doc-values {:doc_values true, :type "float"},
 :end-date {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            :store "yes",
            :type "date"},
 :end-coordinate-2 {:type "double"},
 :size-doc-values {:doc_values true,
                   :store "yes",
                   :type "float"},
 :instrument-sn.lowercase {:index "not_analyzed",
                           :type "string"},
 :collection-concept-id-doc-values {:doc_values true,
                                    :index "not_analyzed",
                                    :store "yes",
                                    :type "string"},
 :lr-north-doc-values {:doc_values true, :type "float"},
 :temporals {:properties {:end-date {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                                     :type "date"},
                          :start-date {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                                       :type "date"}},
             :type "nested",
             :_all {:enabled false},
             :dynamic "strict",
             :_source {:enabled false}},
 :collection-concept-id {:index "not_analyzed",
                         :store "yes",
                         :type "string"},
 :instrument-sn {:index "not_analyzed", :type "string"},
 :instrument-sn.lowercase-doc-values {:doc_values true,
                                      :index "not_analyzed",
                                      :type "string"},
 :update-time {:index "no", :store "yes", :type "string"},
 :day-night.lowercase {:index "not_analyzed",
                       :type "string"},
 :collection-concept-seq-id-doc-values {:doc_values true,
                                        :type "integer"},
 :downloadable {:store "yes",
                :type "boolean",
                :fielddata {:loading "eager"}},
 :atom-links {:index "no", :store "yes", :type "string"},
 :start-coordinate-1 {:type "double"},
 :two-d-coord-name.lowercase {:index "not_analyzed",
                              :type "string"},
 :sensor-sn.lowercase-doc-values {:doc_values true,
                                  :index "not_analyzed",
                                  :type "string"},
 :orbit-asc-crossing-lon {:store "yes", :type "double"},
 :mbr-south {:type "float"},
 :metadata-format {:index "no",
                   :store "yes",
                   :type "string"},
 :orbit-calculated-spatial-domains {:properties {:start-orbit-number {:type "double"},
                                                 :stop-orbit-number {:type "double"},
                                                 :orbital-model-name {:index "not_analyzed",
                                                                      :type "string"},
                                                 :equator-crossing-longitude {:type "double"},
                                                 :orbit-number {:type "integer"},
                                                 :equator-crossing-date-time {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                                                                              :type "date"}},
                                    :type "nested",
                                    :_all {:enabled false},
                                    :dynamic "strict",
                                    :_source {:enabled false}},
 :start-coordinate-2 {:type "double"},
 :provider-id.lowercase {:index "not_analyzed",
                         :type "string"},
 :producer-gran-id {:index "not_analyzed",
                    :store "yes",
                    :type "string"},
 :mbr-north {:type "float"},
 :lr-east {:type "float"},
 :lr-west {:type "float"},
 :size {:store "yes", :type "float"},
 :orbit-end-clat {:type "double"},
 :project-refs {:index "not_analyzed", :type "string"},
 :mbr-east-doc-values {:doc_values true, :type "float"},
 :version-id.lowercase-doc-values {:doc_values true,
                                   :index "not_analyzed",
                                   :type "string"},
 :lr-east-doc-values {:doc_values true, :type "float"},
 :orbit-calculated-spatial-domains-json {:index "no",
                                         :store "yes",
                                         :type "string"},
 :end-date-doc-values {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                       :doc_values true,
                       :store "yes",
                       :type "date"},
 :collection-concept-seq-id {:type "integer"},
 :start-lat {:store "yes", :type "double"},
 :sensor-sn {:index "not_analyzed", :type "string"},
 :provider-id.lowercase-doc-values {:doc_values true,
                                    :index "not_analyzed",
                                    :type "string"},
 :provider-id-doc-values {:doc_values true,
                          :index "not_analyzed",
                          :store "yes",
                          :type "string"},
 :mbr-east {:type "float"},
 :lr-crosses-antimeridian {:type "boolean",
                           :fielddata {:loading "eager"}},
 :granule-ur.lowercase2 {:doc_values true,
                         :index "not_analyzed",
                         :type "string"},
 :end-coordinate-2-doc-values {:doc_values true,
                               :type "double"},
 :end-coordinate-1-doc-values {:doc_values true,
                               :type "double"},
 :platform-sn {:index "not_analyzed", :type "string"},
 :concept-seq-id-doc-values {:doc_values true,
                             :type "integer"},
 :orbit-start-clat {:type "double"},
 :native-id {:doc_values true,
             :index "not_analyzed",
             :type "string"},
 :project-refs.lowercase-doc-values {:doc_values true,
                                     :index "not_analyzed",
                                     :type "string"},
 :feature-id.lowercase {:type "string", :index "not_analyzed"},
 :feature-id {:type "string", :index "not_analyzed"},
 :crid-id.lowercase {:type "string", :index "not_analyzed"},
 :crid-id {:type "string", :index "not_analyzed"}
 :access-value-doc-values {:doc_values true,
                           :store "yes",
                           :type "float"},
 :browsable {:store "yes",
             :type "boolean",
             :fielddata {:loading "eager"}},
 :day-night {:index "not_analyzed",
             :store "yes",
             :type "string"},
 :entry-title.lowercase {:index "not_analyzed",
                         :type "string"},
 :orbit-asc-crossing-lon-doc-values {:doc_values true,
                                     :store "yes",
                                     :type "double"},
 :coordinate-system {:index "no",
                     :store "yes",
                     :type "string"},
 :mbr-west-doc-values {:doc_values true, :type "float"},
 :mbr-west {:type "float"},
 :start-coordinate-2-doc-values {:doc_values true,
                                 :type "double"},
 :start-date {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
              :store "yes",
              :type "date"},
 :concept-id {:index "not_analyzed",
              :store "yes",
              :type "string"},
 :attributes {:properties {:time-value {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                                        :type "date"},
                           :float-value {:type "double"},
                           :datetime-value {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                                            :type "date"},
                           :group {:index "not_analyzed",
                                   :type "string"},
                           :int-value {:type "integer"},
                           :name {:index "not_analyzed",
                                  :type "string"},
                           :group.lowercase {:index "not_analyzed",
                                             :type "string"},
                           :date-value {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                                        :type "date"},
                           :string-value {:index "not_analyzed",
                                          :type "string"},
                           :string-value.lowercase {:index "not_analyzed",
                                                    :type "string"}},
              :type "nested",
              :_all {:enabled false},
              :dynamic "strict",
              :_source {:enabled false}},
 :created-at {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
              :doc_values true,
              :type "date"},
 :platform-sn.lowercase-doc-values {:doc_values true,
                                    :index "not_analyzed",
                                    :type "string"},
 :lr-north {:type "float"},
 :version-id.lowercase {:index "not_analyzed",
                        :type "string"},
 :entry-title {:index "no", :store "yes", :type "string"},
 :day-night-doc-values {:doc_values true,
                        :index "not_analyzed",
                        :store "yes",
                        :type "string"},
 :revision-date {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                 :type "date"},
 :lr-south-doc-values {:doc_values true, :type "float"},
 :start-direction {:index "not_analyzed",
                   :store "yes",
                   :type "string"},
 :two-d-coord-name {:index "not_analyzed",
                    :type "string"},
 :cloud-cover-doc-values {:doc_values true,
                          :store "yes",
                          :type "float"},
 :revision-date-doc-values {:format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                            :doc_values true,
                            :type "date"},
 :revision-id {:type "integer", :store "yes"},
 :native-id-stored {:type "string",
                    :index "not_analyzed",
                    :store "yes",
                    :doc_values true},
 :revision-date-stored-doc-values {:type "date",
                                   :format
                                   "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                                   :store "yes",
                                   :doc_values true}
 :access-value {:store "yes", :type "float"},
 :platform-sn.lowercase {:index "not_analyzed",
                         :type "string"},
 :project-refs.lowercase {:index "not_analyzed",
                          :type "string"},
 :end-lat {:store "yes", :type "double"},
 :lr-west-doc-values {:doc_values true, :type "float"}
 :cycle {:type "integer", :doc_values true},
 :passes
 {:properties
  {:pass {:type "integer", :doc_values true},
   :tiles {:type "string", :index "not_analyzed", :doc_values true}},
  :type "nested",
  :_all {:enabled false},
  :dynamic "strict",
  :_source {:enabled false}}}
