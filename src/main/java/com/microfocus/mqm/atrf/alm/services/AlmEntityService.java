/*
 *     Copyright 2017 EntIT Software LLC, a Micro Focus company, L.P.
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */


package com.microfocus.mqm.atrf.alm.services;

import com.microfocus.mqm.atrf.alm.core.AlmEntity;
import com.microfocus.mqm.atrf.alm.core.AlmEntityCollection;
import com.microfocus.mqm.atrf.alm.core.AlmEntityDescriptor;
import com.microfocus.mqm.atrf.alm.entities.*;
import com.microfocus.mqm.atrf.core.rest.HTTPUtils;
import com.microfocus.mqm.atrf.core.rest.Response;
import com.microfocus.mqm.atrf.core.rest.RestConnector;
import com.microfocus.mqm.atrf.core.rest.RestStatusException;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.*;

/**
 * Created by berkovir on 21/11/2016.
 */
public class AlmEntityService {

    public static final int PAGE_SIZE = 1000;
    public static final int PAGE_SIZE_BY_ID = 200;

    RestConnector restConnector;
    Map<String, String> jsonHeaders = new HashMap<>();
    private String domain;
    private String project;


    Map<String, AlmEntityDescriptor> typesMap = new HashMap<>();

    public AlmEntityService(RestConnector restConnector) {
        this.restConnector = restConnector;
        jsonHeaders.put(HTTPUtils.HEADER_ACCEPT, HTTPUtils.HEADER_APPLICATION_JSON);

        registerTypes();
    }

    private void registerTypes() {

        typesMap.put(Release.TYPE, new ReleaseDescriptor());
        typesMap.put(Run.TYPE, new RunDescriptor());
        typesMap.put(TestSet.TYPE, new TestSetDescriptor());
        typesMap.put(Test.TYPE, new TestDescriptor());
        typesMap.put(Sprint.TYPE, new SprintDescriptor());
        typesMap.put(TestConfiguration.TYPE, new TestConfigurationDescriptor());
        typesMap.put(TestFolder.TYPE, new TestFolderDescriptor());
    }

    public int getTotalNumber(String collectionName, AlmQueryBuilder queryBuilder) {
        String entityCollectionUrl = String.format(AlmRestConstants.ALM_REST_PROJECT_ENTITIES_FORMAT, getDomain(), getProject(), collectionName);

        String queryString = AlmQueryBuilder.create().addSelectedFields(AlmEntity.FIELD_ID).addPageSize(1).addQueryConditions(queryBuilder.getQueryConditions()).build();
        String entitiesCollectionStr = restConnector.httpGet(entityCollectionUrl, Arrays.asList(queryString), jsonHeaders).getResponseData();
        AlmEntityCollection col = parseCollection(entitiesCollectionStr);
        return col.getTotal();
    }

    public List<String> getAllowedProjectsList() {
        String url = String.format(AlmRestConstants.ALM_REST_PROJECTS, getDomain());

        try {
            String json = restConnector.httpGet(url, null, jsonHeaders).getResponseData();
            List<String> projects = parseProjects(json);
            return projects;
        } catch (RestStatusException e) {
            handleExceptionFromALM(e);
        }

        return Collections.emptyList();
    }

    public String getAttachmentsByParent(String id) throws Exception {
        Map<String, String> requestHeaders = new HashMap<String, String>();

        /* A get operation that specifies via accept header that
           we must have an application/octet-stream reply.
           An alt query parameter could also have been used. */
        requestHeaders.put("Accept", "application/octet-stream");
        String url = restConnector.getBaseUrl()+"/run/"+id+"/attachments/";

        Response readResponse =
                restConnector.httpGet(url, null, requestHeaders);

        if (readResponse.getStatusCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception(readResponse.toString());
        }

        return readResponse.getResponseData();
    }
    private void handleExceptionFromALM(RuntimeException e) {
        String errorMsg;
        try {
            String msg = e.getMessage();
            JSONObject jsonObj = new JSONObject(msg);
            errorMsg = (String) jsonObj.get("Title");
        } catch (Exception parseEx) {
            throw e;
        }
        throw new AlmRestException(errorMsg);
    }

    public AlmEntityCollection getEntities(String collectionName, AlmQueryBuilder qb) {
        String entityCollectionUrl = String.format(AlmRestConstants.ALM_REST_PROJECT_ENTITIES_FORMAT, getDomain(), getProject(), collectionName);
        String queryString = qb.build();

        String json = restConnector.httpGet(entityCollectionUrl, Arrays.asList(queryString), jsonHeaders).getResponseData();
        AlmEntityCollection coll = parseCollection(json);
        return coll;
    }

    public List<AlmEntity> getAllPagedEntities(String collectionName, AlmQueryBuilder qb) {

        List<AlmEntity> entities = new ArrayList<>();

        //get num of pages
        int totalNumOfPages = Integer.MAX_VALUE;
        int currentStartIndex = 1;

        for (int i = 1; i <= totalNumOfPages; i++) {
            AlmQueryBuilder myQb = qb.clone().addPageSize(PAGE_SIZE).addStartIndex(currentStartIndex);
            AlmEntityCollection coll = getEntities(collectionName, myQb);
            if (totalNumOfPages == Integer.MAX_VALUE) {
                totalNumOfPages = getNumberOfPages(coll.getTotal(), PAGE_SIZE);
            }

            entities.addAll(coll.getEntities());
            currentStartIndex = i * PAGE_SIZE + 1;
        }

        return entities;
    }

    public static int getNumberOfPages(int totalItems, int pageSize) {
        int ret;
        ret = totalItems / pageSize;
        if (totalItems % pageSize > 0) {
            ret++;
        }

        return ret;
    }

    public List<AlmEntity> getEntitiesByIds(String collectionName, Set<String> ids, Collection<String> fields) {
        List<String> list = new ArrayList<>(ids);
        List<AlmEntity> allEntities = new ArrayList<>();
        for (int i = 0; i < list.size(); i = i + PAGE_SIZE_BY_ID) {
            int maxIndex = Math.min(i + PAGE_SIZE_BY_ID, list.size());
            List<String> subList = list.subList(i, maxIndex);
            AlmQueryBuilder qb = AlmQueryBuilder.create().addQueryCondition("id", StringUtils.join(subList, " OR ")).addSelectedFields(fields);
            AlmEntityCollection coll = getEntities(collectionName, qb);
            allEntities.addAll(coll.getEntities());
        }

        return allEntities;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getDomain() {
        return domain;
    }

    public String getProject() {
        return project;
    }

    private AlmEntityCollection parseCollection(String entitiesCollectionStr) {
        AlmEntityCollection coll = new AlmEntityCollection();
        JSONObject jsonObj = new JSONObject(entitiesCollectionStr);
        int total = jsonObj.getInt("TotalResults");
        coll.setTotal(total);

        JSONArray entitiesJArr = jsonObj.getJSONArray("entities");
        for (int i = 0; i < entitiesJArr.length(); i++) {

            JSONObject entObj = entitiesJArr.getJSONObject(i);
            String type = entObj.getString("Type");

            AlmEntity almEntity = createEntity(type);


            JSONArray fieldsJArr = entObj.getJSONArray("Fields");
            for (int j = 0; j < fieldsJArr.length(); j++) {
                JSONObject fieldObj = fieldsJArr.getJSONObject(j);
                String name = fieldObj.getString("Name");
                JSONArray valuesArr = fieldObj.getJSONArray("values");
                boolean filled = false;
                if (valuesArr.length() > 0) {
                    JSONObject valueObj = valuesArr.getJSONObject(0);
                    if (valueObj.has("value")) {
                        Object value = valueObj.get("value");
                        almEntity.put(name, value);
                        filled = true;
                    }
                }

                if (!filled) {
                    //the field has no value - just set it with null value
                    almEntity.put(name, null);
                }
            }

            //almEntity.put(AlmEntity.FIELD_URL, generateALMReferenceURL(almEntity));
            coll.getEntities().add(almEntity);
        }

        return coll;
    }

    private List<String> parseProjects(String projectsjson) {
        List<String> projectList = new ArrayList<>();
        JSONObject jsonObj = new JSONObject(projectsjson);

        JSONArray projectArr = null;
        //single project
        if (jsonObj.has("Project")) {
            projectArr = (JSONArray) jsonObj.get("Project");
        } else {
            //several projects
            JSONObject projectsObj = (JSONObject) jsonObj.get("Projects");
            if (projectsObj.get("Project") instanceof JSONArray) {
                projectArr = (JSONArray) projectsObj.get("Project");
            } else {
                //single project
                projectArr = new JSONArray();
                projectArr.put(projectsObj.get("Project"));
            }

        }
        for (int i = 0; i < projectArr.length(); i++) {
            JSONObject entObj = projectArr.getJSONObject(i);
            String name = entObj.getString("Name");
            projectList.add(name);
        }


        return projectList;
    }

    private AlmEntity createEntity(String type) {
        AlmEntityDescriptor descriptor = typesMap.get(type);
        if (descriptor == null) {
            throw new RuntimeException("Unregistered type " + type);
        }
        AlmEntity entity = null;
        try {
            entity = descriptor.getEntityClass().getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create entity of type " + type, e);
        }

        return entity;
    }

    public boolean login(String user, String password) {
        boolean ret = false;

        restConnector.clearAll();

        if (password == null) {
            password = "";
        }

        //Get LWSSO COOKIE
        String xml = String.format(AlmRestConstants.ALM_AUTH_XML, user, password);
        Map<String, String> headers = new HashMap<>();
        headers.put(HTTPUtils.HEADER_CONTENT_TYPE, HTTPUtils.HEADER_APPLICATION_XML);
        headers.put(HTTPUtils.HEADER_ACCEPT, HTTPUtils.HEADER_APPLICATION_XML);
        Response authResponse = restConnector.httpPost(AlmRestConstants.ALM_REST_AUTHENTICATION, xml, headers);
        if (authResponse.getStatusCode() == HttpStatus.SC_OK) {
            //GET SESSION cookies
            Response sessionResponse = restConnector.httpPost(AlmRestConstants.ALM_REST_SESSION, null, null);
            if (sessionResponse.getStatusCode() == HttpStatus.SC_CREATED) {
                ret = true;
            }
        }


        return ret;
    }

    public String generateALMReferenceURL(AlmEntity entity) {
        AlmEntityDescriptor descriptor = typesMap.get(entity.getType());

        String protocol = restConnector.getBaseUrl().split("://", 2)[0];
        String base = restConnector.getBaseUrl().split("://", 2)[1];
        String tdProtocol;

        switch (protocol.toLowerCase()) {
            case "http":
            default:
                tdProtocol = "td";
                break;

            case "https":
                tdProtocol = "tds";
                break;
        }

        String url = String.format(descriptor.getAlmRefUrlFormat(), tdProtocol, project, domain, base, entity.getId());
        return url;
    }
}
