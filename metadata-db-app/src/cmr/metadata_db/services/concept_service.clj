(ns cmr.metadata-db.services.concept-service
  "Services to support the business logic of the metadata db."
  (:require
   [clj-time.core :as t]
   [clojure.set :as set]
   [clojure.string]
   [cmr.common.concepts :as cu]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.log :refer (debug error info warn trace)]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.messages :as cmsg]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.data.concepts :as c]
   [cmr.metadata-db.data.ingest-events :as ingest-events]
   [cmr.metadata-db.data.providers :as provider-db]
   [cmr.metadata-db.services.concept-constraints :as cc]
   [cmr.metadata-db.services.concept-validations :as cv]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.provider-service :as provider-service]
   [cmr.metadata-db.services.search-service :as search]
   [cmr.metadata-db.services.util :as util])
  ;; Required to get code loaded
  ;; XXX This is really awful, and we do it a lot in the CMR. What we've got
  ;;     as a result of this is a leak from implementation into a separate part
  ;;     of the code ... not to mention that whatever is happing is is 100%
  ;;     implicit and chock-a-block with side effects. I believe the reason we
  ;;     do this is to work around issues with multimethods. We really need to
  ;;     refactor our multimethod code -- and this isn't the only reason
  ;;     (multimethods use slower code under the hood). If we really do need
  ;;     the flexible polymorphism that multimethods provide, then let's
  ;;     rethink our code reuse strategy around multimethods.
  (:require
   [cmr.metadata-db.data.oracle.concepts.acl]
   [cmr.metadata-db.data.oracle.concepts.collection]
   [cmr.metadata-db.data.oracle.concepts.granule]
   [cmr.metadata-db.data.oracle.concepts.group]
   [cmr.metadata-db.data.oracle.concepts.humanizer]
   [cmr.metadata-db.data.oracle.concepts.subscription]
   [cmr.metadata-db.data.oracle.concepts.service-association]
   [cmr.metadata-db.data.oracle.concepts.service]
   [cmr.metadata-db.data.oracle.concepts.tag-association]
   [cmr.metadata-db.data.oracle.concepts.tag]
   [cmr.metadata-db.data.oracle.concepts.tool]
   [cmr.metadata-db.data.oracle.concepts.variable-association]
   [cmr.metadata-db.data.oracle.concepts.variable]
   [cmr.metadata-db.data.oracle.concepts]
   [cmr.metadata-db.data.oracle.providers]
   [cmr.metadata-db.data.oracle.search]))

(def num-revisions-to-keep-per-concept-type
  "Number of revisions to keep by concept-type. If a concept instance has more than the number
  of revisions here the oldest ones will be deleted."
  {:acl 10
   :collection 10
   :granule 1
   :tag 10
   :tag-association 10
   :access-group 10
   :humanizer 10
   :subscription 1
   :variable 10
   :variable-association 10
   :service 10
   :tool 10
   :service-association 10})

(defconfig days-to-keep-tombstone
  "Number of days to keep a tombstone before is removed from the database."
  {:default 365
   :type Long})

(def concept-truncation-batch-size
  "Maximum number of concepts to process in each iteration of the delete old concepts job."
  50000)

(def system-level-concept-types
  "A set of concept types that only exist on system level provider CMR."
  #{:tag :tag-association :humanizer :variable-association :service-association})

;;; utility methods

(defn validate-system-level-concept
  "Validates that system level concepts are only associated with the system level provider,
  throws an error otherwise."
  [concept provider]
  (let [{concept-type :concept-type} concept
        {provider-id :provider-id} provider]
    (when (and (contains? system-level-concept-types concept-type)
               (not (:system-level? provider)))
      (let [err-msg (case concept-type
                      :tag (msg/tags-only-system-level provider-id)
                      :tag-association (msg/tag-associations-only-system-level provider-id)
                      :humanizer (msg/humanizers-only-system-level provider-id)
                      :variable-association (msg/variable-associations-only-system-level
                                             provider-id)
                      :service-association (msg/service-associations-only-system-level
                                             provider-id))]
        (errors/throw-service-errors :invalid-data [err-msg])))))

(defn- provider-ids-for-validation
  "Returns the set of provider-ids for validation purpose. It is a list of existing provider ids
  and 'CMR', which is reserved for tags / tag associations."
  [db]
  (set (conj (map :provider-id (provider-db/get-providers db)) "CMR")))

(defn- validate-providers-exist
  "Validates that all of the providers in the list exist."
  [db provider-ids]
  (let [existing-provider-ids (provider-ids-for-validation db)
        unknown-providers (set/difference (set provider-ids) existing-provider-ids)]
    (when (> (count unknown-providers) 0)
      (errors/throw-service-error :not-found (msg/providers-do-not-exist unknown-providers)))))

(defmulti set-or-generate-concept-id
  "Get an existing concept-id from the DB for the given concept or generate one if the concept
  has never been saved. Also validate the parent collection concept-id for granule concept."
  (fn [db provider concept]
    (:concept-type concept)))

(defmethod set-or-generate-concept-id :default
  [db provider concept]
  (if (:concept-id concept)
    concept
    (let [concept-id (c/get-concept-id db (:concept-type concept) provider (:native-id concept))
          concept-id (if concept-id concept-id (c/generate-concept-id db concept))]
      (assoc concept :concept-id concept-id))))

(defmethod set-or-generate-concept-id :granule
  [db provider concept]
  (if (:concept-id concept)
    concept
    (let [[existing-concept-id coll-concept-id deleted] (c/get-granule-concept-ids
                                                         db provider (:native-id concept))
          concept-id (if existing-concept-id existing-concept-id (c/generate-concept-id db concept))
          parent-concept-id (get-in concept [:extra-fields :parent-collection-id])]
      (if (and existing-concept-id
               (not= coll-concept-id parent-concept-id)
               (not deleted))
        (errors/throw-service-error
         :invalid-data (msg/granule-collection-cannot-change coll-concept-id parent-concept-id))
        (assoc concept :concept-id concept-id)))))

(defn- set-created-at-for-concept
  "Set the created-at of the given concept to the value of its previous revision if exists."
  [db provider concept]
  (let [{:keys [concept-id concept-type]} concept
        existing-created-at (:created-at
                             (c/get-concept db concept-type provider concept-id))]
    (if existing-created-at
      (assoc concept :created-at existing-created-at)
      concept)))

(defmulti set-created-at
  "Get the existing created-at value for the given concept and set it if it exists. Otherwise
  created-at will be set when saving to the Oracle database."
  (fn [db provider concept]
    (:concept-type concept)))

(defmethod set-created-at :collection
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(defmethod set-created-at :granule
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(defmethod set-created-at :service
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(defmethod set-created-at :tool
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(defmethod set-created-at :subscription
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(defmethod set-created-at :variable
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(defmethod set-created-at :granule
  [db provider concept & previous-revision]
  (let [{:keys [concept-id concept-type]} concept
        previous-revision (first previous-revision)
        existing-created-at (:created-at (or previous-revision
                                             (c/get-concept db concept-type provider concept-id)))]
    (if existing-created-at
      (assoc concept :created-at existing-created-at)
      concept)))

(defmethod set-created-at :default
  [_db _provider concept]
  concept)

(defn- set-or-generate-revision-id
  "Get the next available revision id from the DB for the given concept or
  one if the concept has never been saved."
  [db provider concept & previous-revision]
  (if (:revision-id concept)
    concept
    (let [{:keys [concept-id concept-type provider-id]} concept
          previous-revision (first previous-revision)
          existing-revision-id (:revision-id (or previous-revision
                                                 (c/get-concept db concept-type provider concept-id)))
          revision-id (if existing-revision-id (inc existing-revision-id) 1)]
      (assoc concept :revision-id revision-id))))

(defn- check-concept-revision-id
  "Checks that the revision-id for a concept is greater than
  the current maximum revision-id for this concept."
  [db provider concept previous-revision]
  (let [{:keys [concept-id concept-type revision-id]} concept
        latest-revision (or previous-revision
                            (c/get-concept db concept-type provider concept-id)
                            ;; or it doesn't exist and the next should be 1
                            {:revision-id 0})
        minimum-revision-id (inc (:revision-id latest-revision))]
    (if (>= revision-id minimum-revision-id)
      {:status :pass}
      {:status :fail
       :concept-id (:concept-id latest-revision)
       :expected minimum-revision-id})))

(defn- validate-concept-revision-id
  "Validate that the revision-id for a concept (if given) is greater than the current maximum
  revision-id for this concept. A third argument of the previous revision of the concept can be
  provided to avoid looking up the concept again."
  ([db provider concept]
   (validate-concept-revision-id db provider concept nil))
  ([db provider concept previous-revision]
   (when-let [revision-id (:revision-id concept)]
     (let [result (check-concept-revision-id db provider concept previous-revision)]
       (when (= (:status result) :fail)
         (cmsg/data-error :conflict
                          msg/invalid-revision-id
                          (:concept-id result)
                          (:expected result)
                          revision-id))))))

(defn- invalid-variable-name-msg
  "Returns the message for invalid variable name"
  [new-name existing-name]
  (format "Variable name [%s] does not match the existing variable name [%s]"
          new-name existing-name))

(defn- validate-variable-name-not-changed
  "Validate that the concept variable name is the same as the previous concept's variable name."
  [concept previous-concept]
  (let [new-name (get-in concept [:extra-fields :variable-name])
        existing-name (get-in previous-concept [:extra-fields :variable-name])]
    (when (and previous-concept
               (not (util/is-tombstone? previous-concept))
               (not= existing-name new-name))
      (cmsg/data-error :invalid-data invalid-variable-name-msg new-name existing-name))))

;;; this is abstracted here in case we switch to some other mechanism of
;;; marking tombstones
(defn- set-deleted-flag
  "Create a copy of the given and set its deleted flag to the given value.
  Used to create tombstones from concepts and vice-versa."
  [value concept]
  (assoc concept :deleted value))

(defn- handle-save-errors
  "Deal with errors encountered during saves."
  [concept result]
  (case (:error result)
    :revision-id-conflict
    (cmsg/data-error :conflict
                     msg/concept-id-and-revision-id-conflict
                     (:concept-id concept)
                     (:revision-id concept))

    :concept-id-concept-conflict
    (let [{:keys [concept-id concept-type provider-id native-id]} concept
          {:keys [existing-concept-id existing-native-id]} result]
      (cmsg/data-error :conflict
                       msg/concept-exists-with-different-id
                       existing-concept-id
                       existing-native-id
                       concept-id
                       native-id
                       concept-type
                       provider-id))

    :variable-association-not-same-provider
    (cmsg/data-error :can-not-associate
                     str
                     (:error-message result))
    :collection-associated-with-variable-same-name
    (cmsg/data-error :can-not-associate
                     str
                     (:error-message result))

    :variable-associated-with-another-collection
    (cmsg/data-error :can-not-associate
                     str
                     (:error-message result))

    ;; else
    (errors/internal-error! (:error-message result) (:throwable result))))

;; dynamic is here only for testing purposes to test failure cases.
(defn ^:dynamic try-to-save
  "Try to save a concept. The concept must include a revision-id. Ensures that revision-id and
  concept-id constraints are enforced as well as post commit uniqueness constraints. Returns the
  concept if successful, otherwise throws an exception."
  [db provider concept]
  {:pre [(:revision-id concept)]}
  (let [result (c/save-concept db provider concept)
        ;; When there are constraint violations we send in a rollback function to delete the
        ;; concept that had just been saved and then throw an error.
        rollback-fn #(c/force-delete db
                                     (:concept-type concept)
                                     provider
                                     (:concept-id concept)
                                     (:revision-id concept))]
    (if (nil? (:error result))
      (do
        ;; Perform post commit constraint checks - don't perform check if deleting concepts
        (when-not (:deleted concept)
          (cc/perform-post-commit-constraint-checks
            db
            provider
            concept
            rollback-fn))

        ; Always perform a transaction-id post commit constraint check.
        (cc/perform-post-commit-transaction-id-constraint-check
          db
          provider
          concept
          rollback-fn)
        concept)
      (handle-save-errors concept result))))

;;; service methods

(defn get-concept
  "Get a concept by concept-id."
  ([context concept-id]
   (let [db (util/context->db context)
         {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
         provider (provider-service/get-provider-by-id context provider-id true)]
     (or (c/get-concept db concept-type provider concept-id)
         (cmsg/data-error :not-found
                          msg/concept-does-not-exist
                          concept-id))))
  ([context concept-id revision-id]
   (let [db (util/context->db context)
         {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
         provider (provider-service/get-provider-by-id context provider-id true)]
     (or (c/get-concept db concept-type provider concept-id revision-id)
         (cmsg/data-error :not-found
                          msg/concept-with-concept-id-and-rev-id-does-not-exist
                          concept-id
                          revision-id)))))

(defn- latest-revision?
  "Given a concept-id and a revision-id, perform a check whether the
  revision-id represents the most recent revision of the concept."
  [context concept-id revision-id]
  (= revision-id (:revision-id (get-concept context concept-id))))

(defn split-concept-id-revision-id-tuples
  "Divides up concept-id-revision-id-tuples by provider and concept type."
  [concept-id-revision-id-tuples]
  (reduce (fn [m tuple]
            (let [{:keys [concept-type provider-id]} (cu/parse-concept-id (first tuple))]
              (update-in m [provider-id concept-type] #(if %
                                                         (conj % tuple)
                                                         [tuple]))))
          {}
          concept-id-revision-id-tuples))

(defn split-concept-ids
  "Divides up concept-ids by provider and concept type"
  [concept-ids]
  (reduce (fn [m concept-id]
            (let [{:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)]
              (update-in m [provider-id concept-type] #(conj % concept-id))))
          {}
          concept-ids))

(defn- filter-non-existent-providers
  "Removes providers that don't exist from a map of provider-ids to values."
  [db provider-id-map]
  (let [existing-provider-ids (provider-ids-for-validation db)]
    (into {} (filter (comp existing-provider-ids first) provider-id-map))))

(defn get-concepts
  "Get multiple concepts by concept-id and revision-id. Returns concepts in order requested"
  [context concept-id-revision-id-tuples allow-missing?]
  (info (format "Getting [%d] concepts by concept-id/revision-id"
                (count concept-id-revision-id-tuples)))
  (let [start (System/currentTimeMillis)
        parallel-chunk-size (get-in context [:system :parallel-chunk-size])
        db (util/context->db context)
        ;; Split the tuples so they can be requested separately for each provider and concept type
        split-tuples-map (split-concept-id-revision-id-tuples concept-id-revision-id-tuples)
        split-tuples-map (if allow-missing?
                           (filter-non-existent-providers db split-tuples-map)
                           (do
                             (validate-providers-exist db (keys split-tuples-map))
                             split-tuples-map))]

    (let [concepts (apply concat
                          (for [[provider-id concept-type-tuples-map] split-tuples-map
                                [concept-type tuples] concept-type-tuples-map]
                            (let [provider (provider-service/get-provider-by-id
                                            context provider-id true)]
                              ;; Retrieve the concepts for this type and provider id.
                              (if (and (> parallel-chunk-size 0)
                                       (< parallel-chunk-size (count tuples)))
                                ;; retrieving chunks in parallel for faster read performance
                                (apply concat
                                       (cutil/pmap-n-all
                                         (partial c/get-concepts db concept-type provider)
                                         parallel-chunk-size
                                         tuples))
                                (c/get-concepts db concept-type provider tuples)))))
          ;; Create a map of tuples to concepts
          concepts-by-tuple (into {} (for [c concepts] [[(:concept-id c) (:revision-id c)] c]))]
      (if (or allow-missing? (= (count concepts) (count concept-id-revision-id-tuples)))
        ;; Return the concepts in the order they were requested
        (let [millis (- (System/currentTimeMillis) start)]
          (info (format "Found [%d] concepts in [%d] ms" (count concepts) millis))
          (keep concepts-by-tuple concept-id-revision-id-tuples))
        ;; some concepts weren't found
        (let [missing-concept-tuples (set/difference (set concept-id-revision-id-tuples)
                                                     (set (keys concepts-by-tuple)))]
          (errors/throw-service-errors
            :not-found
            (map (partial apply msg/concept-with-concept-id-and-rev-id-does-not-exist)
                 missing-concept-tuples)))))))

(defn get-latest-concepts
  "Get the latest version of concepts by specifiying a list of concept-ids. Results are
  returned in the order requested"
  [context concept-ids allow-missing?]
  (info (format "Getting [%d] latest concepts by concept-id" (count concept-ids)))
  (let [start (System/currentTimeMillis)
        parallel-chunk-size (get-in context [:system :parallel-chunk-size])
        db (util/context->db context)
        ;; Split the concept-ids so they can be requested separately for each provider and concept type
        split-concept-ids-map (split-concept-ids concept-ids)
        split-concept-ids-map (if allow-missing?
                                (filter-non-existent-providers db split-concept-ids-map)
                                (do
                                  (validate-providers-exist db (keys split-concept-ids-map))
                                  split-concept-ids-map))]
    (let [concepts (apply concat
                          (for [[provider-id concept-type-concept-id-map] split-concept-ids-map
                                [concept-type cids] concept-type-concept-id-map]
                            (let [provider (provider-service/get-provider-by-id
                                            context provider-id true)]
                              ;; Retrieve the concepts for this type and provider id.
                              (if (and (> parallel-chunk-size 0)
                                       (< parallel-chunk-size (count cids)))
                                ;; retrieving chunks in parallel for faster read performance
                                (apply concat
                                       (cutil/pmap-n-all
                                         (partial c/get-latest-concepts db concept-type provider)
                                         parallel-chunk-size
                                         cids))
                                (c/get-latest-concepts db concept-type provider cids)))))
          ;; Create a map of concept-ids to concepts
          concepts-by-concept-id (into {} (for [c concepts] [(:concept-id c) c]))]
      (if (or allow-missing? (= (count concepts) (count concept-ids)))
        ;; Return the concepts in the order they were requested
        (let [millis (- (System/currentTimeMillis) start)]
          (info (format "Found [%d] concepts in [%d] ms" (count concepts) millis))
          (keep concepts-by-concept-id concept-ids))
        ;; some concepts weren't found
        (let [missing-concept-ids (set/difference (set concept-ids)
                                                  (set (keys concepts-by-concept-id)))]
          (errors/throw-service-errors
            :not-found
            (map msg/concept-does-not-exist
                 missing-concept-ids)))))))

(defn get-expired-collections-concept-ids
  "Returns the concept ids of expired collections in the provider."
  [context provider-id]
  (let [db (util/context->db context)
        provider (provider-service/get-provider-by-id context provider-id true)]
    (distinct (map :concept-id (c/get-expired-concepts db provider :collection)))))

(defmulti save-concept-revision
  "Store a concept record, which could be a tombstone, and return the revision."
  (fn [context concept]
    (boolean (:deleted concept))))

(defn- tombstone-associations
  "Tombstone the associations that matches the given search params,
  skip-publication? flag controls if association deletion event should be generated,
  skip-publication? true means no association deletion event should be generated."
  [context assoc-type search-params skip-publication?]
  (let [associations (search/find-concepts context search-params)]
    (doseq [association associations]
      (save-concept-revision context {:concept-type assoc-type
                                      :concept-id (:concept-id association)
                                      :deleted true
                                      :user-id "cmr"
                                      :skip-publication skip-publication?}))))

(defn- delete-associations-for-collection-concept
  "Delete the associations associated with the given collection revision and association type,
  no association deletion event is generated."
  [context assoc-type coll-concept-id coll-revision-id]
  (let [search-params (cutil/remove-nil-keys
                       {:concept-type assoc-type
                        :associated-concept-id coll-concept-id
                        :associated-revision-id coll-revision-id
                        :exclude-metadata true
                        :latest true})]
    (tombstone-associations context assoc-type search-params true)))

(defmulti delete-associations
  "Delete the associations of the given association type that is associated with
  the given concept type and concept id.
  assoc-type can be :variable-association or :service-association,
  concept-type can be :collection, :variable, or :service."
  (fn [context concept-type concept-id revision-id assoc-type]
    concept-type))

(defmethod delete-associations :default
  [context concept-type concept-id revision-id assoc-type]
  ;; does nothing by default
  nil)

(defmethod delete-associations :collection
  [context concept-type concept-id revision-id assoc-type]
  ;; only delete the associated variable associations
  ;; if the given revision-id is the latest revision of the collection
  (let [[latest-coll] (search/find-concepts context {:concept-type :collection
                                                     :concept-id concept-id
                                                     :exclude-metadata true
                                                     :latest true})]
    (when (or (nil? revision-id)
              (= revision-id (:revision-id latest-coll)))
      (delete-associations-for-collection-concept context assoc-type concept-id nil))))

(defmethod delete-associations :variable
  [context concept-type concept-id revision-id assoc-type]
  (when (= :variable-association assoc-type)
    (let [search-params (cutil/remove-nil-keys
                         {:concept-type assoc-type
                          :variable-concept-id concept-id
                          :exclude-metadata true
                          :latest true})]
      ;; create variable association tombstones and queue the variable association delete events
      (tombstone-associations context assoc-type search-params false))))

(defmethod delete-associations :service
  [context concept-type concept-id revision-id assoc-type]
  (when (= :service-association assoc-type)
    (let [search-params (cutil/remove-nil-keys
                         {:concept-type assoc-type
                          :service-concept-id concept-id
                          :exclude-metadata true
                          :latest true})]
      ;; create variable association tombstones and queue the variable association delete events
      (tombstone-associations context assoc-type search-params false))))

;; true implies creation of tombstone for the revision
(defmethod save-concept-revision true
  [context concept]
  (cv/validate-tombstone-request concept)
  (let [{:keys [concept-id revision-id revision-date user-id skip-publication]} concept
        {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
        db (util/context->db context)
        provider (provider-service/get-provider-by-id context provider-id true)
        _ (validate-system-level-concept concept provider)
        previous-revision (c/get-concept db concept-type provider concept-id)]
    (if previous-revision
      ;; For a concept which is already deleted (i.e. previous revision is a tombstone),
      ;; new revision is created only if the revision id is supplied. We don't want extraneous
      ;; tombstones created which, for example, can happen if multiple delete requests are sent at
      ;; once for the same concept. But if a revision id is sent we need to validate it and store a
      ;; record in the database. The revision id allows a client (including virutal product service)
      ;; to send concept updates and deletions out of order.
      (if (and (util/is-tombstone? previous-revision) (nil? revision-id))
        previous-revision
        (let [tombstone (merge previous-revision {:concept-id concept-id
                                                  :revision-id revision-id
                                                  :revision-date revision-date
                                                  :user-id user-id
                                                  :metadata ""
                                                  :deleted true})]
          (cv/validate-concept tombstone)
          (validate-concept-revision-id db provider tombstone previous-revision)
          (let [revisioned-tombstone (->> (set-or-generate-revision-id db provider tombstone previous-revision)
                                          (try-to-save db provider))]
            ;; delete the associated variable associations if applicable
            (delete-associations context concept-type concept-id nil :variable-association)
            ;; delete the associated service associations if applicable
            (delete-associations context concept-type concept-id nil :service-association)

            ;; skip publication flag is set for tag association when its associated
            ;; collection revision is force deleted. In this case, the association is no longer
            ;; needed to be indexed, so we don't publish the deletion event.
            ;; We can't let the message get published because by the time indexer get the message,
            ;; the associated collection revision is gone and indexer won't be able to find it.
            ;; The association is potentially created by a different user than the provider,
            ;; so a collection revision is force deleted doesn't necessarily mean that the
            ;; association is no longer needed. People might want to see what is in the old
            ;; association potentially and force deleting it seems to run against the rationale
            ;; that we introduced revisions in the first place.
            ;; skip publication flag is also set for variable association when its associated
            ;; collection revision is deleted. Indexer will index the variables associated with
            ;; the collection through the collection delete event. Not the variable association
            ;; delete event.
            (when-not skip-publication
              (ingest-events/publish-event
               context (ingest-events/concept-delete-event revisioned-tombstone)))
            revisioned-tombstone)))
      (if revision-id
        (cmsg/data-error :not-found
                         msg/concept-with-concept-id-and-rev-id-does-not-exist
                         concept-id
                         revision-id)
        (cmsg/data-error :not-found
                         msg/concept-does-not-exist
                         concept-id)))))

(defn- update-service-associations
  "Create a new revision for the non-tombstoned service associations that is related to the
  given concept; Does noting if the given concept is not a service concept."
  [context concept-type concept-id]
  (when (= :service concept-type)
    (let [search-params (cutil/remove-nil-keys
                         {:concept-type :service-association
                          :service-concept-id concept-id
                          :exclude-metadata true
                          :latest true})
          associations (filter #(= false (:deleted %))
                               (search/find-concepts context search-params))]
      (doseq [association associations]
        (save-concept-revision
         context
         (-> association
             (dissoc :revision-id :revision-date :transaction-id)
             ;; set user-id to cmr to indicate the association is created by CMR
             (assoc :user-id "cmr")))))))

;; false implies creation of a non-tombstone revision
(defmethod save-concept-revision false
  [context concept]
  (trace "concept:" (keys concept))
  (trace "provider id:" (:provider-id concept))
  (cv/validate-concept concept)
  (let [db (util/context->db context)
        provider-id (or (:provider-id concept)
                        (when (contains? system-level-concept-types (:concept-type concept)) "CMR"))
        ;; Need this for tags/tag-associations since they don't have a provider-id in their
        ;; concept maps, but later processing requires it.
        concept (assoc concept :provider-id provider-id)
        provider (provider-service/get-provider-by-id context provider-id true)
        _ (validate-system-level-concept concept provider)
        concept (->> concept
                     (set-or-generate-concept-id db provider)
                     (set-created-at db provider))
        {:keys [concept-type concept-id]} concept]

    (validate-concept-revision-id db provider concept)
    ;; validate variable name
    (when (= :variable concept-type)
      (let [previous-concept (c/get-concept db concept-type provider concept-id)]
        (validate-variable-name-not-changed concept previous-concept)))

    (let [concept (->> concept
                       (set-or-generate-revision-id db provider)
                       (set-deleted-flag false)
                       (try-to-save db provider))
          revision-id (:revision-id concept)]
      ;; publish tombstone delete event if the previous concept revision is a granule tombstone
      (when (and (= :granule concept-type)
                 (> revision-id 1))
        (let [previous-concept (c/get-concept db concept-type provider concept-id (- revision-id 1))]
          (when (util/is-tombstone? previous-concept)
            (ingest-events/publish-tombstone-delete-msg
             context concept-type concept-id revision-id))))

      ;; update service associatons if applicable, i.e. when the concept is a service,
      ;; so that the collections can be updated in elasticsearch with the updated service info
      (update-service-associations context concept-type concept-id)
      (ingest-events/publish-event
       context
       (ingest-events/concept-update-event concept))
      concept)))

(defn- delete-associated-tag-associations
  "Delete the tag associations associated with the given collection revision,
  no tag association deletion event is generated."
  [context coll-concept-id coll-revision-id]
  (delete-associations-for-collection-concept
   context :tag-association coll-concept-id coll-revision-id))

(defmulti force-delete-cascading-events
  "Performs the cascading events of the force deletion of a concept"
  (fn [context concept-type concept-id revision-id]
    concept-type))

(defmethod force-delete-cascading-events :collection
  [context concept-type concept-id revision-id]
  ;; delete the related tag associations and variable associations
  (delete-associated-tag-associations context concept-id revision-id)
  (delete-associations context concept-type concept-id revision-id :variable-association)
  (delete-associations context concept-type concept-id revision-id :service-association)
  (ingest-events/publish-concept-revision-delete-msg context concept-id revision-id))

(defmethod force-delete-cascading-events :variable
  [context concept-type concept-id revision-id]
  (ingest-events/publish-concept-revision-delete-msg context concept-id revision-id))

(defmethod force-delete-cascading-events :subscription
  [context concept-type concept-id revision-id]
  (ingest-events/publish-concept-revision-delete-msg context concept-id revision-id))

(defmethod force-delete-cascading-events :tool
  [context concept-type concept-id revision-id]
  (ingest-events/publish-concept-revision-delete-msg context concept-id revision-id))

(defmethod force-delete-cascading-events :service
  [context concept-type concept-id revision-id]
  (ingest-events/publish-concept-revision-delete-msg context concept-id revision-id))

(defmethod force-delete-cascading-events :default
  [context concept-type concept-id revision-id]
  ;; does nothing in default
  nil)

(defn force-delete
  "Remove a revision of a concept from the database completely."
  [context concept-id revision-id force?]
  (let [db (util/context->db context)
        {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
        provider (provider-service/get-provider-by-id context provider-id true)
        concept (c/get-concept db concept-type provider concept-id revision-id)]
    (if concept
      (if (and (not force?)
               (latest-revision? context concept-id revision-id))
        (errors/throw-service-error
         :bad-request
         (format (str "Cannot force delete the latest revision of a concept "
                      "[%s, %s], use regular delete instead.")
                 concept-id revision-id))
        (do
          (force-delete-cascading-events context concept-type concept-id revision-id)
          (c/force-delete db concept-type provider concept-id revision-id)))
      (cmsg/data-error :not-found
                       msg/concept-with-concept-id-and-rev-id-does-not-exist
                       concept-id
                       revision-id))
    {:concept-id concept-id
     :revision-id revision-id}))

(defn reset
  "Delete all concepts from the concept store and all providers."
  [context]
  (provider-service/reset-providers context)
  (c/reset (util/context->db context)))

(defn get-concept-id
  "Get a concept id for a given concept."
  [context concept-type provider-id native-id]
  (cu/validate-concept-type concept-type)
  (let [db (util/context->db context)
        provider (provider-service/get-provider-by-id context provider-id true)
        concept-id (c/get-concept-id db concept-type provider native-id)]
    (if concept-id
      concept-id
      (cmsg/data-error :not-found
                       msg/missing-concept-id
                       concept-type
                       provider-id
                       native-id))))

(defn- get-provider-to-collection-map
  "Returns a map of the provider ids to collection concepts that exist in the database."
  [context]
  (let [db (util/context->db context)]
    (into {} (pmap (fn [{:keys [provider-id] :as provider}]
                     [provider-id
                      (->> (c/find-latest-concepts db provider {:provider-id provider-id
                                                                :concept-type :collection})
                           (remove :deleted))])
                   (provider-db/get-providers db)))))

;; There's not sufficient integration tests for this. Filed CMR-1579
(defn get-provider-holdings
  "Gets provider holdings within Metadata DB"
  [context]
  (let [db (util/context->db context)
        ;; Create a map of provider to collection concepts
        provider-to-collections (get-provider-to-collection-map context)
        ;; Get a map of provider id to counts of granules per collection concept id
        provider-to-count-maps (into {}
                                     (pmap (fn [{:keys [provider-id] :as provider}]
                                             [provider-id (c/get-concept-type-counts-by-collection
                                                            db :granule provider)])
                                           (provider-db/get-providers db)))]
    (for [[provider-id collections] provider-to-collections
          collection collections
          :let [concept-id (:concept-id collection)
                granule-count (get-in provider-to-count-maps
                                      [provider-id concept-id]
                                      0)]]
      {:provider-id provider-id
       :concept-id concept-id
       :granule-count granule-count
       :entry-title (get-in collection [:extra-fields :entry-title])})))

(defn delete-expired-concepts
  "Delete concepts that have not been deleted and have a delete-time before now"
  [context provider concept-type]
  (let [db (util/context->db context)
        ;; atom to store concepts to skip on subsequent recursions
        failed-concept-ids (atom #{})
        ;; return expired concepts which have not previously failed
        get-expired (fn []
                      (remove
                        #(contains? @failed-concept-ids (:concept-id %))
                        (c/get-expired-concepts db provider concept-type)))]
    (loop []
      (when-let [expired-concepts (seq (get-expired))]
        (info "Deleting expired" (name concept-type) "concepts:" (map :concept-id expired-concepts))
        (doseq [c expired-concepts]
          (let [tombstone (-> c
                              (update-in [:revision-id] inc)
                              (assoc :deleted true :metadata ""))]
            (try
              (try-to-save db provider tombstone)
              (catch clojure.lang.ExceptionInfo e
                ;; Re-throw anything other than a simple conflict.
                (when-not (-> e ex-data :type (= :conflict))
                  (throw e))
                ;; If an update comes in for one of the items we are
                ;; deleting, it will result in a conflict, in that
                ;; case we just want to log a warning and store the
                ;; failed concept-id in order avoid an infinite loop.
                (warn e "Conflict when saving expired concept tombstone")
                (swap! failed-concept-ids conj (:concept-id c))))))
        (recur)))))

(defn- call-force-deletes
  "Calls functions that do the deletion of concepts or that publish events to message queue."
  [context db provider concept-type tombstone-delete? concept-id-revision-id-tuples concept-truncation-batch-size]
  (when concept-id-revision-id-tuples
    (info "Deleting" (count concept-id-revision-id-tuples)
          "old concept revisions for provider" (:provider-id provider))
    (when (and tombstone-delete?
               (= :granule concept-type))
      ;; Remove any reference to granule from deleted-granule index
      (doseq [[concept-id revision-id] concept-id-revision-id-tuples]
        (ingest-events/publish-tombstone-delete-msg context concept-type concept-id revision-id)))
    (doseq [[concept-id revision-id] concept-id-revision-id-tuples]
      ;; performs the cascading delete actions first
      (force-delete-cascading-events context concept-type concept-id (long revision-id)))
    (c/force-delete-concepts db provider concept-type concept-id-revision-id-tuples)))

(defn force-delete-with
  "Continually force deletes concepts using the given function concept-id-revision-id-tuple-finder
  to find concept id revision id tuples to delete. Stops once the function returns an empty set."
  [context provider concept-type tombstone-delete? concept-id-revision-id-tuple-finder concept-truncation-batch-size]
  (let [db (util/context->db context)]
    (loop [concept-id-revision-id-tuples (seq (concept-id-revision-id-tuple-finder))]
      (if (< (count concept-id-revision-id-tuples) concept-truncation-batch-size)
        (call-force-deletes
          context db provider concept-type tombstone-delete? concept-id-revision-id-tuples concept-truncation-batch-size)
        (do
          (call-force-deletes
            context db provider concept-type tombstone-delete? concept-id-revision-id-tuples concept-truncation-batch-size)
          (recur (seq (concept-id-revision-id-tuple-finder))))))))

(defn delete-old-revisions
  "Delete concepts to keep a fixed number of revisions around. It also deletes old tombstones that
  are older than a fixed number of days and any prior revisions of the deleted tombstone."
  [context provider concept-type]
  (let [db (util/context->db context)
        concept-type-name (str (name concept-type) "s")
        tombstone-cut-off-date (t/minus (time-keeper/now) (t/days (days-to-keep-tombstone)))]

    ;; We only want to publish the deleted-tombstone event in the case where a granule tombstone is
    ;; being cleaned up, not when an old revision is being removed, because the case of old revision,
    ;; a deleted-tombstone even would have been published already.
    (info "Starting deletion of old" concept-type-name "for provider" (:provider-id provider))
    (force-delete-with
      context provider concept-type false
      #(c/get-old-concept-revisions
         db
         provider
         concept-type
         (get num-revisions-to-keep-per-concept-type
              concept-type)
         concept-truncation-batch-size)
      concept-truncation-batch-size)

    (info "Starting deletion of tombstoned" concept-type-name "for provider" (:provider-id provider))
    (force-delete-with
      context provider concept-type true
      #(c/get-tombstoned-concept-revisions
         db
         provider
         concept-type
         tombstone-cut-off-date
         concept-truncation-batch-size)
      concept-truncation-batch-size)))
