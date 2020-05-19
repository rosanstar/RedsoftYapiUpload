package com.redsoft.idea.plugin.yapiv2.upload;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jgoodies.common.base.Strings;
import com.redsoft.idea.plugin.yapiv2.constant.YApiConstants;
import com.redsoft.idea.plugin.yapiv2.model.YApiCatMenuParam;
import com.redsoft.idea.plugin.yapiv2.model.YApiCatResponse;
import com.redsoft.idea.plugin.yapiv2.model.YApiHeader;
import com.redsoft.idea.plugin.yapiv2.model.YApiResponse;
import com.redsoft.idea.plugin.yapiv2.model.YApiSaveParam;
import com.redsoft.idea.plugin.yapiv2.util.HttpClientUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

/**
 * 上传到yapi
 *
 * @date 2019/1/31 11:41 AM
 */
public class YApiUpload {


    private Gson gson = new Gson();

    public static Map<String, Map<String, Integer>> catMap = new HashMap<>();

    /**
     * @description 调用保存接口
     * @date 2019/5/15
     */
    public YApiResponse uploadSave(YApiSaveParam yapiSaveParam,
            @SuppressWarnings("unused") String path)
            throws IOException {
        if (Strings.isEmpty(yapiSaveParam.getTitle())) {
            yapiSaveParam.setTitle(yapiSaveParam.getPath());
        }
        YApiHeader yapiHeader = new YApiHeader();
        if ("form".equals(yapiSaveParam.getReq_body_type())) {
            yapiHeader.setName("Content-Type");
            yapiHeader.setValue("application/x-www-form-urlencoded");
        } else {
            yapiHeader.setName("Content-Type");
            yapiHeader.setValue("application/json");
//            yapiSaveParam.setReq_body_type("json");
        }
        if (Objects.isNull(yapiSaveParam.getReq_headers())) {
            Set<YApiHeader> list = new HashSet<>();
            list.add(yapiHeader);
            yapiSaveParam.setReq_headers(list);
        } else {
            yapiSaveParam.getReq_headers().add(yapiHeader);
        }
        YApiResponse yapiResponse = this.getCatIdOrCreate(yapiSaveParam);
        if (yapiResponse.getErrcode() == 0 && yapiResponse.getData() != null) {
            yapiSaveParam.setCatid(String.valueOf(yapiResponse.getData()));
            String response = HttpClientUtils
                    .ObjectToString(HttpClientUtils.getHttpclient().execute(
                            this.getHttpPost(yapiSaveParam.getYApiUrl() + YApiConstants.yapiSave,
                                    gson.toJson(yapiSaveParam))), "utf-8");
            return gson.fromJson(response, YApiResponse.class);
        } else {
            return yapiResponse;
        }
    }


    /**
     * 获得httpPost
     */
    private HttpPost getHttpPost(String url, String body) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-type", "application/json;charset=utf-8");
        HttpEntity reqEntity = new StringEntity(body == null ? "" : body, "UTF-8");
        httpPost.setEntity(reqEntity);
        return httpPost;
    }

    private HttpGet getHttpGet(String url) {
        return HttpClientUtils
                .getHttpGet(url, "application/json", "application/json; charset=utf-8");
    }


    /**
     * @description 获得分类或者创建分类
     * @date 2019/5/15
     */
    public YApiResponse getCatIdOrCreate(YApiSaveParam yapiSaveParam) {
        Map<String, Integer> catMenuMap = catMap.get(yapiSaveParam.getProjectId().toString());
        if (catMenuMap != null) {
            if (!Strings.isEmpty(yapiSaveParam.getMenu())) {
                if (Objects.nonNull(catMenuMap.get(yapiSaveParam.getMenu()))) {
                    return new YApiResponse(catMenuMap.get(yapiSaveParam.getMenu()));
                }
            } else {
                if (Objects.nonNull(catMenuMap.get(YApiConstants.menu))) {
                    return new YApiResponse(catMenuMap.get(YApiConstants.menu));
                }
                yapiSaveParam.setMenu(YApiConstants.menu);
            }
        }
        String response;
        try {
            response = HttpClientUtils.ObjectToString(HttpClientUtils.getHttpclient().execute(
                    this.getHttpGet(
                            yapiSaveParam.getYApiUrl() + YApiConstants.yapiCatMenu + "?project_id="
                                    + yapiSaveParam
                                    .getProjectId() + "&token=" + yapiSaveParam.getToken())),
                    "utf-8");
            YApiResponse yapiResponse = gson.fromJson(response, YApiResponse.class);
            Map<String, Integer> catMenuMapSub = catMap
                    .get(yapiSaveParam.getProjectId().toString());
            if (yapiResponse.getErrcode() == 0) {
                @SuppressWarnings("unchecked")
                List<YApiCatResponse> list = (List<YApiCatResponse>) yapiResponse.getData();
                list = gson.fromJson(gson.toJson(list), new TypeToken<List<YApiCatResponse>>() {
                }.getType());
                for (YApiCatResponse yapiCatResponse : list) {
                    if (yapiCatResponse.getName().equals(yapiSaveParam.getMenu())) {
                        this.addMenu(catMenuMapSub, yapiCatResponse, yapiSaveParam);
                        return new YApiResponse(yapiCatResponse.get_id());
                    }
                }
            }
            YApiCatMenuParam yapiCatMenuParam = new YApiCatMenuParam(yapiSaveParam.getMenu(),
                    yapiSaveParam.getProjectId(), yapiSaveParam.getToken());
            String menuDesc = yapiSaveParam.getMenuDesc();
            if (Objects.nonNull(menuDesc)) {
                yapiCatMenuParam.setDesc(menuDesc);
            }
            String responseCat = HttpClientUtils
                    .ObjectToString(HttpClientUtils.getHttpclient().execute(
                            this.getHttpPost(yapiSaveParam.getYApiUrl() + YApiConstants.yapiAddCat,
                                    gson.toJson(yapiCatMenuParam))), "utf-8");
            YApiCatResponse yapiCatResponse = gson
                    .fromJson(gson.fromJson(responseCat, YApiResponse.class).getData().toString(),
                            YApiCatResponse.class);
            this.addMenu(catMenuMapSub, yapiCatResponse, yapiSaveParam);
            return new YApiResponse(yapiCatResponse.get_id());
        } catch (IOException e) {
            e.printStackTrace();
            return new YApiResponse(0, e.toString());
        }
    }

    private void addMenu(Map<String, Integer> catMenuMapSub, YApiCatResponse yapiCatResponse,
            YApiSaveParam yapiSaveParam) {
        if (catMenuMapSub != null) {
            catMenuMapSub.put(yapiCatResponse.getName(), yapiCatResponse.get_id());
        } else {
            catMenuMapSub = new HashMap<>();
            catMenuMapSub.put(yapiCatResponse.getName(), yapiCatResponse.get_id());
            catMap.put(yapiSaveParam.getProjectId().toString(), catMenuMapSub);
        }
    }


}