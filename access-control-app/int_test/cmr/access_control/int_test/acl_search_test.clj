(ns cmr.access-control.int-test.acl-search-test
  (:require
    [clj-http.client :as client]
    [clj-time.core :as t]
    [clojure.test :refer :all]
    [cmr.access-control.data.access-control-index :as access-control-index]
    [cmr.access-control.int-test.fixtures :as fixtures]
    [cmr.access-control.test.util :as u]
    [cmr.common.util :as util :refer [are3]]
    [cmr.elastic-utils.config :as elastic-config]
    [cmr.mock-echo.client.echo-util :as e]
    [cmr.transmit.access-control :as ac]))

(use-fixtures :each
  (fixtures/reset-fixture {"prov1guid" "PROV1", "prov2guid" "PROV2", "prov3guid" "PROV3",
                           "prov4guid" "PROV4"}
                          ["user1" "user2" "user3" "user4" "user5"])
  (fixtures/grant-all-group-fixture ["prov1guid" "prov2guid"])
  (fixtures/grant-all-acl-fixture))
(use-fixtures :once (fixtures/int-test-fixtures))

(deftest invalid-search-test
  (testing "Accept header"
    (testing "Other than JSON is rejected"
      (is (= {:status 400
              :body {:errors ["The mime types specified in the accept header [application/text] are not supported."]}
              :content-type :json}
             (ac/search-for-acls (u/conn-context) {} {:http-options {:accept "application/text"}
                                                      :raw? true}))))
    (testing "No Accept header is ok"
      (is (= 200
             (:status (ac/search-for-acls (u/conn-context) {} {:http-options {:accept nil} :raw? true}))))))
  (testing "Unknown parameters are rejected"
    (is (= {:status 400
            :body {:errors ["Parameter [foo] was not recognized."]}
            :content-type :json}
           (ac/search-for-acls (u/conn-context) {:foo "bar"} {:raw? true})))))

(defn- generate-query-map-for-group-permissions
  "Returns a query map generated from group permission pairs.
  group-permissions should be a seqeuence of group/user-identifer permission pairs such as
  [\"guest\" \"read\" \"AG10000-PROV1\" \"create\" \"registered\" \"order\"]"
  [group-permissions]
  (first (reduce (fn [[m count] [group permission]]
                   [(assoc-in m
                              [:group-permission (keyword (str count))]
                              {:permitted-group group
                               :permission permission})
                    (inc count)])
                 [{} 0]
                 (partition 2 group-permissions))))

(deftest acl-search-order-test
  ;; Conforms to requirements set out in CMR-3590, alphabetical order regardless of case
  (let [token (e/login (u/conn-context) "user1")
        acl1 (u/ingest-acl token {:group_permissions [{:user_type "registered" :permissions ["read"]}]
                                  :catalog_item_identity {:provider_id "PROV1"
                                                          :name "b Lowercase B"
                                                          :collection_applicable true}})
        acl2 (u/ingest-acl token {:group_permissions [{:user_type "registered" :permissions ["read"]}]
                                  :catalog_item_identity {:provider_id "PROV1"
                                                          :name "A Uppercase A"
                                                          :collection_applicable true}})
        acl3 (u/ingest-acl token {:group_permissions [{:user_type "registered" :permissions ["read"]}]
                                  :catalog_item_identity {:provider_id "PROV1"
                                                          :name "1 numbered"
                                                          :collection_applicable true}})
        get-name #(get-in % [:catalog_item_identity :name])]
    (proto-repl.saved-values/save 1)
    (is (= [(get-name acl3) (get-name acl2) (get-name acl1)
            "Provider - PROV1 - CATALOG_ITEM_ACL"
            "System - ANY_ACL"]           
           (map :name (:items (ac/search-for-acls (merge {:token token} (u/conn-context)) {})))))))

(deftest acl-search-permission-test
  (let [token (e/login (u/conn-context) "user1")
        group1 (u/ingest-group token
                               {:name "any acl read"}
                               ["user1"])
        group2 (u/ingest-group token
                               {:name "without any acl read"}
                               ["user2"])
        group3 (u/ingest-group token
                               {:name "provider object prov1 read"}
                               ["user3"])
        group1-concept-id (:concept_id group1)
        group2-concept-id (:concept_id group2)
        group3-concept-id (:concept_id group3)

        ;; remove ANY_ACL read to all users
        _ (ac/update-acl (u/conn-context) (:concept-id fixtures/*fixture-system-acl*)
                         (assoc-in (u/system-acl "ANY_ACL")
                                   [:group_permissions 0]
                                   {:permissions ["read" "create"] :group_id group1-concept-id}))

        acl1 (u/ingest-acl token (assoc-in (u/system-acl "INGEST_MANAGEMENT_ACL")
                                         [:group_permissions 0]
                                         {:permissions ["read"] :group_id group1-concept-id}))
        acl2 (u/ingest-acl token (assoc-in (u/system-acl "ARCHIVE_RECORD")
                                         [:group_permissions 0]
                                         {:permissions ["delete"] :group_id group2-concept-id}))
        acl3 (u/ingest-acl token (u/system-acl "SYSTEM_OPTION_DEFINITION_DEPRECATION"))
        acl4 (u/ingest-acl token (assoc (u/provider-acl "PROVIDER_OBJECT_ACL")
                                      :group_permissions
                                      [{:group_id group3-concept-id :permissions ["read"]}]))
        acl5 (u/ingest-acl token (u/provider-acl "OPTION_DEFINITION"))
        acl6 (u/ingest-acl token (assoc-in (u/provider-acl "OPTION_DEFINITION")
                                         [:provider_identity :provider_id] "PROV2"))
        ;; Create an ACL with a catalog item identity for PROV1
        acl7 (u/ingest-acl token {:group_permissions [{:user_type "registered" :permissions ["read"]}]
                                  :catalog_item_identity {:provider_id "PROV1"
                                                          :name "PROV1 All Collections ACL"
                                                          :collection_applicable true}})]

    (testing "Provider Object ACL permissions"
      (let [token (e/login (u/conn-context) "user3")
            response (ac/search-for-acls (merge {:token token} (u/conn-context)) {:provider "PROV1"})]
        (is (= (u/acls->search-response 4 [fixtures/*fixture-provider-acl* acl4 acl5 acl7])
               (dissoc response :took)))))
    (testing "Guest search permission"
      (let [token (e/login-guest (u/conn-context))
            response (ac/search-for-acls (merge {:token token} (u/conn-context)) {})]
        (is (= (u/acls->search-response 1 [acl7])
               (dissoc response :took)))))
    (testing "User search permission"
      (let [token (e/login (u/conn-context) "user1")
            response (ac/search-for-acls (merge {:token token} (u/conn-context)) {})]
        (is (= (u/acls->search-response 9 [fixtures/*fixture-provider-acl* (assoc fixtures/*fixture-system-acl* :revision_id 2) acl1 acl2 acl3 acl4 acl5 acl6 acl7])
               (dissoc response :took)))))
    (testing "Search permission without ANY_ACL read"
      (let [token (e/login (u/conn-context) "user2")
            response (ac/search-for-acls (merge {:token token} (u/conn-context)) {})]
        (is (= (u/acls->search-response 1 [acl7])
               (dissoc response :took)))))))

(deftest acl-search-test
  (let [token (e/login (u/conn-context) "user1")
        group1 (u/ingest-group token
                               {:name "group1"}
                               ["user1"])
        group2 (u/ingest-group token
                               {:name "group2"}
                               ["user1"])
        group1-concept-id (:concept_id group1)
        group2-concept-id (:concept_id group2)
        acl1 (u/ingest-acl token (assoc (u/system-acl "SYSTEM_AUDIT_REPORT")
                                      :group_permissions
                                      [{:user_type "guest" :permissions ["read"]}]))
        acl2 (u/ingest-acl token (assoc (u/system-acl "METRIC_DATA_POINT_SAMPLE")
                                      :group_permissions
                                      [{:user_type "guest" :permissions ["read"]}]))
        acl3 (u/ingest-acl token (u/system-acl "SYSTEM_INITIALIZER"))
        acl4 (u/ingest-acl token (assoc (u/system-acl "ARCHIVE_RECORD")
                                      :group_permissions
                                      [{:user_type "guest" :permissions ["delete"]}]))

        acl5 (u/ingest-acl token (assoc (u/provider-acl "AUDIT_REPORT")
                                      :group_permissions
                                      [{:user_type "guest" :permissions ["read"]}]))

        acl6 (u/ingest-acl token (u/single-instance-acl group1-concept-id))
        acl7 (u/ingest-acl token (u/single-instance-acl group2-concept-id))

        acl8 (u/ingest-acl token (u/catalog-item-acl "All Collections"))
        acl9 (u/ingest-acl token (u/catalog-item-acl "All Granules"))

        system-acls [fixtures/*fixture-system-acl* acl1 acl2 acl3 acl4]
        provider-acls [fixtures/*fixture-provider-acl* acl5]
        single-instance-acls [acl6 acl7]
        catalog-item-acls [acl8 acl9]
        all-acls (concat system-acls provider-acls single-instance-acls catalog-item-acls)]

    (testing "Find all ACLs"
      (let [response (ac/search-for-acls (u/conn-context) {:page_size 20})]
        (is (= (u/acls->search-response (count all-acls) all-acls)
               (dissoc response :took)))
        (testing "Expected Names"
          ;; We verify the exact expected names here to ensure that they look correct.
          (is (= ["All Collections"
                  "All Granules"
                  (str "Group - " group1-concept-id)
                  (str "Group - " group2-concept-id)
                  "Provider - PROV1 - AUDIT_REPORT"
                  "Provider - PROV1 - CATALOG_ITEM_ACL"
                  "System - ANY_ACL"
                  "System - ARCHIVE_RECORD"
                  "System - METRIC_DATA_POINT_SAMPLE"
                  "System - SYSTEM_AUDIT_REPORT"
                  "System - SYSTEM_INITIALIZER"]
                 (map :name (:items response)))))))
    (testing "Find acls with full search response"
      (let [response (ac/search-for-acls (u/conn-context) {:include_full_acl true :page_size 20})]
        (is (= (u/acls->search-response (count all-acls) all-acls {:include-full-acl true})
               (dissoc response :took)))))
    (testing "ACL Search Paging"
      (testing "Page Size"
        (is (= (u/acls->search-response (count all-acls) all-acls {:page-size 4 :page-num 1})
               (dissoc (ac/search-for-acls (u/conn-context) {:page_size 4}) :took))))
      (testing "Page Number"
        (is (= (u/acls->search-response (count all-acls) all-acls {:page-size 4 :page-num 2})
               (dissoc (ac/search-for-acls (u/conn-context) {:page_size 4 :page_num 2}) :took)))))))

(deftest acl-search-by-any-id-test
  (let [token (e/login (u/conn-context) "user1")
        acl1 (u/ingest-acl token (u/catalog-item-acl "All Collections"))
        acl2 (u/ingest-acl token (u/catalog-item-acl "All Granules"))
        acl3 (u/ingest-acl token (assoc (u/catalog-item-acl "Some More Collections")
                                        :legacy_guid "acl3-legacy-guid"))]
    (are3 [names params]
      (is (= (set names)
             (set
               (map :name
                    (:items
                      (ac/search-for-acls (u/conn-context) params))))))

      "by concept ID"
      ["All Collections" "All Granules"] {:id [(:concept-id acl1) (:concept-id acl2)]}

      "by legacy guid"
      ["Some More Collections"] {:id "acl3-legacy-guid"}

      "by both"
      ["All Collections" "All Granules" "Some More Collections"]
      {:id [(:concept-id acl1)
            (:concept-id acl2)
            "acl3-legacy-guid"]})))

(deftest acl-search-permitted-group-test
  (let [token (e/login (u/conn-context) "user1")
        acl1 (u/ingest-acl token (assoc (u/system-acl "SYSTEM_AUDIT_REPORT")
                                      :group_permissions
                                      [{:user_type "guest" :permissions ["read"]}]))
        acl2 (u/ingest-acl token (assoc (u/system-acl "METRIC_DATA_POINT_SAMPLE")
                                      :group_permissions
                                      [{:user_type "registered" :permissions ["read"]}]))
        acl3 (u/ingest-acl token (assoc (u/system-acl "GROUP")
                                      :group_permissions
                                      [{:group_id "AG12345-PROV" :permissions ["create" "read"]}
                                       {:user_type "guest" :permissions ["read"]}]))
        acl4 (u/ingest-acl token (assoc (u/provider-acl "AUDIT_REPORT")
                                      :group_permissions
                                      [{:user_type "guest" :permissions ["read"]}]))
        acl5 (u/ingest-acl token (assoc (u/provider-acl "OPTION_DEFINITION")
                                      :group_permissions
                                      [{:user_type "registered" :permissions ["create"]}]))
        acl6 (u/ingest-acl token (assoc (u/provider-acl "OPTION_ASSIGNMENT")
                                      :group_permissions
                                      [{:group_id "AG12345-PROV" :permissions ["delete"]}]))

        acl7 (u/ingest-acl token (u/catalog-item-acl "All Collections"))
        acl8 (u/ingest-acl token (assoc (u/catalog-item-acl "All Granules")
                                      :group_permissions
                                      [{:user_type "registered" :permissions ["read" "order"]}
                                       {:group_id "AG10000-PROV" :permissions ["create"]}]))

        guest-acls [fixtures/*fixture-system-acl* fixtures/*fixture-provider-acl* acl1 acl3 acl4 acl7]
        registered-acls [fixtures/*fixture-system-acl* fixtures/*fixture-provider-acl* acl2 acl5 acl8]
        AG12345-acls [acl3 acl6]
        AG10000-acls [acl8]
        read-acls [fixtures/*fixture-system-acl* fixtures/*fixture-provider-acl* acl1 acl2 acl3 acl4 acl8]
        create-acls [fixtures/*fixture-system-acl* fixtures/*fixture-provider-acl* acl3 acl5 acl7 acl8]
        all-acls [fixtures/*fixture-system-acl* fixtures/*fixture-provider-acl* acl1 acl2 acl3 acl4 acl5 acl6 acl7 acl8]]

    (testing "Search ACLs by permitted group"
      (are [permitted-groups acls]
           (let [response (ac/search-for-acls (u/conn-context) {:permitted-group permitted-groups})]
             (= (u/acls->search-response (count acls) acls)
                (dissoc response :took)))

           ["guest"] guest-acls
           ["registered"] registered-acls
           ["AG12345-PROV"] AG12345-acls
           ["AG10000-PROV"] AG10000-acls
           ;; permitted-group search is case insensitive by default
           ["REGISTERED" "AG10000-PROV"] registered-acls
           ["GUEST" "AG10000-PROV"] (concat guest-acls AG10000-acls)
           ["AG12345-PROV" "AG10000-PROV"] (concat AG12345-acls AG10000-acls)
           ["guest" "registered" "AG12345-PROV" "AG10000-PROV"] all-acls))

    (testing "Search ACLs by permitted group with options"
      (are [permitted-groups options acls]
           (let [response (ac/search-for-acls (u/conn-context)
                                              (merge {:permitted-group permitted-groups} options))]
             (= (u/acls->search-response (count acls) acls)
                (dissoc response :took)))

           ["GUEST"] {"options[permitted_group][ignore_case]" true} guest-acls
           ["GUEST"] {"options[permitted_group][ignore_case]" false} []))

    (testing "Search ACLs by permitted group with invalid values"
      (are [permitted-groups invalid-msg]
        (= {:status 400
            :body {:errors [(format "Parameter permitted_group has invalid values [%s]. Only 'guest', 'registered' or a group concept id can be specified."
                                    invalid-msg)]}
            :content-type :json}
           (ac/search-for-acls (u/conn-context) {:permitted-group permitted-groups} {:raw? true}))

        ["gust"] "gust"
        ["GUST" "registered" "AG10000-PROV" "G10000-PROV"] "GUST, G10000-PROV"))

    (testing "Search ACLs by group permission"
      (are3 [group-permissions acls]
           (let [query-map (generate-query-map-for-group-permissions group-permissions)
                 response (ac/search-for-acls (u/conn-context) query-map)]
             (is (= (u/acls->search-response (count acls) acls)
                    (dissoc response :took))))
           ;; CMR-3154 acceptance criterium 1
           "Guests create"
           ["guest" "create"] [fixtures/*fixture-provider-acl* fixtures/*fixture-system-acl* acl7]

           "Guest read"
           ["guest" "read"] [fixtures/*fixture-provider-acl* fixtures/*fixture-system-acl* acl1 acl3 acl4]

           "Registered read"
           ["registered" "read"] [fixtures/*fixture-provider-acl* fixtures/*fixture-system-acl* acl2 acl8]

           "Group create"
           ["AG10000-PROV" "create"] [acl8]

           "Registered order"
           ["registered" "order"] [acl8]

           "Group create"
           ["AG12345-PROV" "create"] [acl3]

           "Another group create"
           ["AG10000-PROV" "create"] AG10000-acls

           "Group read"
           ["AG12345-PROV" "read"] [acl3]

           "Group delete"
           ["AG12345-PROV" "delete"] [acl6]

           "Case-insensitive group create"
           ["AG10000-PROV" "CREATE"] AG10000-acls

           ;; CMR-3154 acceptance criterium 2
           "Registered read or registered create"
           ["registered" "read" "registered" "create"] registered-acls

           "Registered read or group AG12345-PROV delete"
           ["registered" "read" "AG12345-PROV" "delete"] [fixtures/*fixture-provider-acl* fixtures/*fixture-system-acl* acl2 acl6 acl8]))

    ;; CMR-3154 acceptance criterium 3
    (testing "Search ACLs by group permission just group or permission"
      (are3 [query-map acls]
        (let [response (ac/search-for-acls (u/conn-context) {:group-permission {:0 query-map}})]
          (is (= (u/acls->search-response (count acls) acls)
                 (dissoc response :took))))
        "Just user type"
        {:permitted-group "guest"} guest-acls

        "Just group"
        {:permitted-group "AG10000-PROV"} AG10000-acls

        "Just read permission"
        {:permission "read"} read-acls

        "Just create permission"
        {:permission "create"} create-acls))

    ;; CMR-3154 acceptance criterium 4
    (testing "Search ACLS by group permission with non integer index is an error"
      (let [query {:group-permission {:foo {:permitted-group "guest" :permission "read"}}}]
        (is (= {:status 400
                :body {:errors ["Parameter group_permission has invalid index value [foo]. Only integers greater than or equal to zero may be specified."]}
                :content-type :json}
               (ac/search-for-acls (u/conn-context) query {:raw? true})))))

    ;; CMR-3154 acceptance criterium 5
    (testing "Search ACLS by group permission with subfield other than permitted_group or permission is an error"
      (let [query {:group-permission {:0 {:allowed-group "guest" :permission "read"}}}]
        (is (= {:status 400
                :body {:errors ["Parameter group_permission has invalid subfield [allowed_group]. Only 'permitted_group' and 'permission' are allowed."]}
                :content-type :json}
               (ac/search-for-acls (u/conn-context) query {:raw? true})))))

    (testing "Search ACLS by group permission with invalid permitted_group"
      (let [query {:group-permission {:0 {:permitted_group "foo" :permission "read"}}}]
        (is (= {:status 400
                :body {:errors ["Sub-parameter permitted_group of parameter group_permissions has invalid values [foo]. Only 'guest', 'registered' or a group concept id may be specified."]}
                :content-type :json}
               (ac/search-for-acls (u/conn-context) query {:raw? true})))))

    (testing "Searching ACLS by group permission with permission values other than read, create, update, delete, or order is an error"
      (let [query {:group-permission {:0 {:permitted_group "guest" :permission "foo"}}}]
        (is (= {:status 400
                :body {:errors ["Sub-parameter permission of parameter group_permissions has invalid values [foo]. Only 'read', 'update', 'create', 'delete', or 'order' may be specified."]}
                :content-type :json}
               (ac/search-for-acls (u/conn-context) query {:raw? true})))))))

(deftest acl-search-by-identity-type-test
  (let [token (e/login (u/conn-context) "user1")
        group1 (u/ingest-group token
                               {:name "group1"}
                               ["user1"])
        group1-concept-id (:concept_id group1)
        acl-single-instance (u/ingest-acl token (u/single-instance-acl group1-concept-id))
        acl-catalog-item (u/ingest-acl token (u/catalog-item-acl "All Collections"))
        all-acls [fixtures/*fixture-system-acl* fixtures/*fixture-provider-acl* acl-single-instance acl-catalog-item]]

    (testing "Search with invalid identity type returns error"
      (is (= {:status 400
              :body {:errors [(str "Parameter identity_type has invalid values [foo]. "
                                   "Only 'provider', 'system', 'single_instance', or 'catalog_item' can be specified.")]}
              :content-type :json}
             (ac/search-for-acls (u/conn-context) {:identity-type "foo"} {:raw? true}))))

    (testing "Search with valid identity types"
      (are3 [identity-types expected-acls]
        (let [response (ac/search-for-acls (u/conn-context) {:identity-type identity-types})]
          (is (= (u/acls->search-response (count expected-acls) expected-acls)
                 (dissoc response :took))))

        "Identity type 'provider'"
        ["provider"] [fixtures/*fixture-provider-acl*]

        "Identity type 'system'"
        ["system"] [fixtures/*fixture-system-acl*]

        "Identity type 'single_instance'"
        ["single_instance"] [acl-single-instance]

        "Identity type 'catalog_item'"
        ["catalog_item"] [acl-catalog-item]

        "Multiple identity types"
        ["provider" "single_instance"] [fixtures/*fixture-provider-acl* acl-single-instance]

        "All identity types"
        ["provider" "system" "single_instance" "catalog_item"] all-acls

        "Identity type searches are always case-insensitive"
        ["PrOvIdEr"] [fixtures/*fixture-provider-acl*]))))

(deftest acl-search-by-permitted-user-test
  (let [token (e/login (u/conn-context) "user1")
        group1 (u/ingest-group token {:name "group1"} ["user1"])
        group2 (u/ingest-group token {:name "group2"} ["USER1" "user2"])
        group3 (u/ingest-group token {:name "group3"} nil)
        ;; No user should match this since all users are registered
        acl-registered-1 (u/ingest-acl token (assoc (u/system-acl "METRIC_DATA_POINT_SAMPLE")
                                                  :group_permissions
                                                  [{:user_type "registered" :permissions ["read"]}]))
        acl-group1 (u/ingest-acl token (assoc (u/system-acl "ARCHIVE_RECORD")
                                            :group_permissions
                                            [{:group_id (:concept_id group1) :permissions ["delete"]}]))
        acl-registered-2 (u/ingest-acl token (assoc (u/provider-acl "OPTION_DEFINITION")
                                                  :group_permissions
                                                  [{:user_type "registered" :permissions ["create"]}]))
        acl-group2 (u/ingest-acl token (assoc (u/provider-acl "OPTION_ASSIGNMENT")
                                            :group_permissions
                                            [{:group_id (:concept_id group2) :permissions ["create"]}]))
        ;; No user should match this acl since group3 has no members
        acl-group3 (u/ingest-acl token (assoc (u/catalog-item-acl "All Granules")
                                            :group_permissions
                                            [{:group_id (:concept_id group3) :permissions ["create"]}]))

        registered-acls [acl-registered-1 acl-registered-2 fixtures/*fixture-provider-acl* fixtures/*fixture-system-acl*]]

    (testing "Search with non-existent user returns error"
      (are3 [user]
        (is (= {:status 400
                :body {:errors [(format "The following users do not exist [%s]" user)]}
                :content-type :json}
               (ac/search-for-acls (u/conn-context) {:permitted-user user} {:raw? true})))

        "Invalid user"
        "foo"

        "'guest' is not a registered user"
        "guest"

        "'registered' is not a registered user either"
        "registered"))

    (testing "Search with valid users"
      (are3 [users expected-acls]
        (let [response (ac/search-for-acls (u/conn-context) {:permitted-user users})]
          (is (= (u/acls->search-response (count expected-acls) expected-acls)
                 (dissoc response :took))))

        "user3 is not in a group, but gets acls for registered but not guest"
        ["user3"] (concat registered-acls)

        "user1 gets acls for registered, group1, and group2"
        ["user1"] [fixtures/*fixture-system-acl* fixtures/*fixture-provider-acl* acl-registered-1 acl-registered-2 acl-group1 acl-group2]

        "user2 gets acls for guest, registered, and group2"
        ["user2"] [fixtures/*fixture-system-acl* fixtures/*fixture-provider-acl* acl-registered-1 acl-registered-2 acl-group2]

        "User names are case-insensitive"
        ["USER1"] [fixtures/*fixture-system-acl* fixtures/*fixture-provider-acl* acl-registered-1 acl-registered-2 acl-group1 acl-group2]))))

(deftest acl-search-provider-test
  (let [token (e/login (u/conn-context) "user1")
        group1 (u/ingest-group token {:name "group1"} ["user1"])
        acl1 (u/ingest-acl token (assoc-in (assoc-in (u/provider-acl "CATALOG_ITEM_ACL")
                                                   [:group_permissions 0] {:group_id (:concept_id group1)
                                                                           :permissions ["create"]})
                                         [:provider_identity :provider_id] "PROV2"))
        acl2 (u/ingest-acl token (assoc-in (assoc-in (u/provider-acl "CATALOG_ITEM_ACL")
                                                   [:group_permissions 0] {:group_id (:concept_id group1)
                                                                           :permissions ["create"]})
                                         [:provider_identity :provider_id] "PROV3"))
        acl3 (u/ingest-acl token (assoc-in (assoc-in (u/provider-acl "CATALOG_ITEM_ACL")
                                                   [:group_permissions 0] {:group_id (:concept_id group1)
                                                                           :permissions ["create"]})
                                         [:provider_identity :provider_id] "PROV4"))
        acl4 (u/ingest-acl token (u/catalog-item-acl "Catalog_Item1_PROV1"))
        acl5 (u/ingest-acl token (u/catalog-item-acl "Catalog_Item2_PROV1"))
        acl6 (u/ingest-acl token (assoc-in (u/catalog-item-acl "Catalog_Item3_PROV2")
                                         [:catalog_item_identity :provider_id] "PROV2"))
        acl7 (u/ingest-acl token (assoc-in (u/catalog-item-acl "Catalog_Item5_PROV2")
                                         [:catalog_item_identity :provider_id] "PROV2"))

        acl8 (u/ingest-acl token (assoc-in (u/catalog-item-acl "Catalog_Item6_PROV4")
                                         [:catalog_item_identity :provider_id] "PROV4"))
        prov1-acls [fixtures/*fixture-provider-acl* acl4 acl5]
        prov1-and-2-acls [fixtures/*fixture-provider-acl* acl1 acl4 acl5 acl6 acl7]
        prov3-acls [acl2]]

    (testing "Search ACLs that grant permissions to objects owned by a single provider
              or by any provider where multiple are specified"
      (are3 [provider-ids acls]
        (let [response (ac/search-for-acls (u/conn-context) {:provider provider-ids})]
          (is (= (u/acls->search-response (count acls) acls)
                 (dissoc response :took))))

        "Single provider with multiple results"
        ["PROV1"] prov1-acls

        "Single provider with multiple results, case-insensitive"
        ["prov1"] prov1-acls

        "Multiple providers with multiple results"
        ["PROV1" "PROV2"] prov1-and-2-acls

        "Multiple providers with multiple results, case-insensitive"
        ["prov1" "prov2"] prov1-and-2-acls

        "Single provider with single result"
        ["PROV3"] prov3-acls

        "Single provider with single result, case-insensitive"
        ["prov3"] prov3-acls

        "Provider that doesn't exist"
        ["NOT_A_PROVIDER"] []))

    (testing "Search ACLs by provider with options"
      (are3 [provider-ids options acls]
       (let [response (ac/search-for-acls (u/conn-context)
                                          (merge {:provider provider-ids} options))]
         (is (= (u/acls->search-response (count acls) acls)
                (dissoc response :took))))

       "Multiple providers with multiple results using ignore_case=false option"
       ["PROV1"] {"options[provider][ignore_case]" false} prov1-acls

       "Multiple providers with multiple results using ignore_case=true option"
       ["prov1"] {"options[provider][ignore_case]" true} prov1-acls

       "Multiple providers with multiple results using ignore_case=false option"
       ["PROV1"] {"options[provider][ignore_case]" true} prov1-acls

       "Multiple providers with empty results using ignore_case=false option"
       ["prov1"] {"options[provider][ignore_case]" false} []))))

(deftest acl-search-multiple-criteria
  (let [token (e/login (u/conn-context) "user1")
        group1 (u/ingest-group token {:name "group1"} ["user1"])
        group2 (u/ingest-group token {:name "group2"} ["user2"])
        acl1 (u/ingest-acl token (assoc (u/system-acl "METRIC_DATA_POINT_SAMPLE")
                                      :group_permissions
                                      [{:user_type "registered" :permissions ["read"]}]))
        acl2 (u/ingest-acl token (assoc (u/system-acl "ARCHIVE_RECORD")
                                      :group_permissions
                                      [{:group_id (:concept_id group1) :permissions ["delete"]}]))
        acl3 (u/ingest-acl token (assoc (u/provider-acl "OPTION_DEFINITION")
                                      :group_permissions
                                      [{:user_type "registered" :permissions ["create"]}]))
        acl4 (u/ingest-acl token (assoc-in (assoc-in (u/provider-acl "CATALOG_ITEM_ACL")
                                                   [:group_permissions 1] {:group_id (:concept_id group1)
                                                                           :permissions ["create"]})
                                         [:provider_identity :provider_id] "PROV2"))
        acl5 (u/ingest-acl token (assoc (u/catalog-item-acl "All Collection")
                                      :group_permissions
                                      [{:group_id (:concept_id group1) :permissions ["create"]}
                                       {:user_type "registered" :permissions ["create"]}]))
        acl6 (u/ingest-acl token (assoc (u/catalog-item-acl "All Granules")
                                      :group_permissions
                                      [{:group_id (:concept_id group1) :permissions ["create"]}]))
        acl7 (u/ingest-acl token (assoc-in (assoc (u/catalog-item-acl "All Granules PROV2")
                                                :group_permissions
                                                [{:group_id (:concept_id group2) :permissions ["create"]}])
                                         [:catalog_item_identity :provider_id] "PROV2"))]
    (testing "Search with every criteria"
      (are3 [params acls]
        (let [response (ac/search-for-acls (u/conn-context) params)]
          (is (= (u/acls->search-response (count acls) acls)
                 (dissoc response :took))))
        "Multiple search criteria"
        {:provider "PROV1"
         :permitted-group "registered"
         :identity-type "catalog_item"
         :permitted-user "user1"}
        [acl5]

        "One criteria changed to get empty result"
        {:provider "PROV1"
         :permitted-group "guest"
         :identity-type "catalog_item"
         :permitted-user "user1"}
        []

        ;;To show that when permitted groups are specified, the groups associated with the user but not included in the permitted groups params are not returned
        "Multiple search criteria with permitted groups being registered and guest but user1 being specified as permitted user"
        {:provider ["PROV1" "PROV2"]
         :permitted-group ["guest" "registered"]
         :identity-type ["catalog_item" "provider"]
         :permitted-user "user1"}
        [acl3 fixtures/*fixture-provider-acl* acl4 acl5]

        ;;Shows when permitted groups are not specified, then all user groups are returned for that user
        "Multiple search criteria with no permitted group specified and permitted users set to user2"
        {:provider ["PROV1" "PROV2"]
         :permitted-group ""
         :identity-type ["catalog_item" "provider"]
         :permitted-user "user2"}
        [acl3 fixtures/*fixture-provider-acl* acl5 acl7]))))

(deftest acl-search-with-legacy-group-guid-test
  (let [token (e/login (u/conn-context) "user1")
        group1-legacy-guid "group1-legacy-guid"
        group1 (u/ingest-group token
                               {:name "group1"
                                :legacy_guid group1-legacy-guid}
                               ["user1"])
        group2 (u/ingest-group token
                               {:name "group2"}
                               ["user1"])
        group1-concept-id (:concept_id group1)
        group2-concept-id (:concept_id group2)

        ;; ACL associated with a group that has legacy guid
        acl1 (u/ingest-acl token (assoc-in (u/system-acl "INGEST_MANAGEMENT_ACL")
                                         [:group_permissions 0]
                                         {:permissions ["read"] :group_id group1-concept-id}))
        ;; ACL associated with a group that does not have legacy guid
        acl2 (u/ingest-acl token (assoc-in (u/system-acl "ARCHIVE_RECORD")
                                         [:group_permissions 0]
                                         {:permissions ["delete"] :group_id group2-concept-id}))
        ;; SingleInstanceIdentity ACL with a group that has legacy guid
        acl3 (u/ingest-acl token (u/single-instance-acl group1-concept-id))
        ;; SingleInstanceIdentity ACL with a group that does not have legacy guid
        acl4 (u/ingest-acl token (u/single-instance-acl group2-concept-id))
        ;; ACL without group_id
        acl5 (u/ingest-acl token (assoc (u/provider-acl "AUDIT_REPORT")
                                      :group_permissions
                                      [{:user_type "guest" :permissions ["read"]}]))

        expected-acls (concat [fixtures/*fixture-system-acl*]
                              [fixtures/*fixture-provider-acl*]
                              [acl1 acl2 acl3 acl4 acl5])

        expected-acl1-with-legacy-guid (assoc-in
                                        acl1 [:group_permissions 0 :group_id] group1-legacy-guid)
        expected-acl3-with-legacy-guid (-> acl3
                                           ;; single-instance-name is added to generate the correct
                                           ;; ACL name for comparison which is based on group concept id
                                           (assoc :single-instance-name (str "Group - " group1-concept-id))
                                           (assoc-in [:single_instance_identity :target_id]
                                                     group1-legacy-guid))
        expected-acls-with-legacy-guids (concat [fixtures/*fixture-system-acl*]
                                                [fixtures/*fixture-provider-acl*]
                                                [expected-acl1-with-legacy-guid acl2
                                                 expected-acl3-with-legacy-guid acl4 acl5])]

    (testing "Find acls without legacy group guid"
      (let [response (ac/search-for-acls (u/conn-context) {:include_full_acl true :page_size 20})]
        (is (= (u/acls->search-response (count expected-acls) expected-acls {:include-full-acl true})
               (dissoc response :took)))))

    (testing "Find acls with legacy group guid"
      (let [response (ac/search-for-acls (u/conn-context) {:include_full_acl true
                                                           :include-legacy-group-guid true
                                                           :page_size 20})]
        (is (= (u/acls->search-response
                (count expected-acls)
                expected-acls-with-legacy-guids {:include-full-acl true
                                                 :include-legacy-group-guid true})
               (dissoc response :took)))))

    (testing "Find acls with include-legacy-group-guid but without include-full-acl"
      (let [{:keys [status body]} (ac/search-for-acls
                                   (u/conn-context) {:include-legacy-group-guid true} {:raw? true})
            errors (:errors body)]
        (is (= 400 status))
        (is (= ["Parameter include_legacy_group_guid can only be used when include_full_acl is true"]
               errors))))))

(deftest acl-reindexing-test
  (u/without-publishing-messages
   (let [token (e/login (u/conn-context) "user1")
         acl1 (u/ingest-acl token (assoc (u/system-acl "METRIC_DATA_POINT_SAMPLE")
                                       :group_permissions
                                       [{:user_type "guest" :permissions ["read"]}]))
         acl1-concept-id (:concept-id acl1)
         acl2 (u/ingest-acl token (assoc (u/system-acl "ARCHIVE_RECORD")
                                       :group_permissions
                                       [{:user_type "guest" :permissions ["delete"]}]))
         acl3 (u/ingest-acl token (assoc (u/provider-acl "AUDIT_REPORT")
                                       :group_permissions
                                       [{:user_type "guest" :permissions ["read"]}]))
         search-for-all-acls (fn []
                               (dissoc (ac/search-for-acls (u/conn-context) {}) :took))
         fixture-acls [fixtures/*fixture-system-acl* fixtures/*fixture-provider-acl*]
         expected-acls-after-reindexing (concat fixture-acls [acl2 acl3])]

     ;; Unindex acl1 directly through elastic to simulate an inconsistent state
     (client/delete (format "http://%s:%s/acls/acl/%s"
                            (elastic-config/elastic-host)
                            (elastic-config/elastic-port)
                            (:concept-id acl1))
                    {:query-params {:refresh true}})

     ;; Before reindexing, every ACL but acl1 is returned
     (is (= (u/acls->search-response 4 (conj fixture-acls acl2 acl3))
            (search-for-all-acls))
         "Found user acls before re-indexing")

     ;; reindex the acls
     (ac/reindex-acls (u/conn-context))
     (u/wait-until-indexed)

     ;; After reindexing, both fixture acls and un-deleted user acls are found.
     (let [actual-response (search-for-all-acls)]
       (is (= (count expected-acls-after-reindexing) (:hits actual-response)))
       (is (= (set (:items (u/acls->search-response
                            (count expected-acls-after-reindexing) expected-acls-after-reindexing)))
              (set (:items actual-response))))))))
