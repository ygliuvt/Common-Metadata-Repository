(ns cmr.system-int-test.bootstrap.virtual-products-test
  (:require [clojure.test :refer :all]
            [cmr.bootstrap.test.catalog-rest :as cat-rest]
            [cmr.common.concepts :as concepts]
            [cmr.common.mime-types :as mime-types]
            [cmr.oracle.connection :as oracle]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util :refer [eval-in-dev-sys]]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.virtual-product-util :as vp]
            [cmr.umm.core :as umm]
            [cmr.virtual-product.config :as vp-config]))

;; test procedure:
;;
;; 1. fixtures create an empty database
;; 2. create providers
;; 3. create source collection
;; 4. create source granules
;; 5. ensure virtual granules do NOT exist
;; 6. run bootstrap virtual products
;; 7. ensure virtual granules DO exist

(defn virtual-products-fixture
  [f]
  (dev-sys-util/reset)
  (doseq [provider-id vp/virtual-product-providers]
    (println "Creating provider" provider-id)
    (ingest/create-provider (str provider-id "-guid") provider-id {:cmr-only true}))
  ;; turn off virtual products using eval-in-dev-sys so that it works
  ;; with integration tests when the CMR is running in another process
  (eval-in-dev-sys
   '(cmr.virtual-product.config/set-virtual-products-enabled! false))
  ;; run the test itself
  (f)
  ;; turn on virtual products again
  (eval-in-dev-sys
   '(cmr.virtual-product.config/set-virtual-products-enabled! true)))

(use-fixtures :each virtual-products-fixture)

(deftest test-virtual-product-bootstrapping
  (let [source-collections (vp/ingest-source-collections)
        source-granules    (doall
                            (for [source-coll source-collections
                                  :let [{:keys [provider-id entry-title]} source-coll]
                                  granule-ur (vp-config/sample-source-granule-urs
                                              [provider-id entry-title])]
                              (d/ingest provider-id (dg/granule source-coll {:granule-ur granule-ur}))))]
    (vp/ingest-virtual-collections source-collections)
    (index/wait-until-indexed)
    (bootstrap/bootstrap-virtual-products)
    (index/wait-until-indexed)
    ;; TODO: Make sure all the appropriate virtual granules were created.
    ))
