package com.lifemetrics.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaForwardControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SpaForwardController()).build();
    }

    @Test
    void 클라이언트_라우트는_index_html_로_포워딩() throws Exception {
        for (String path : new String[]{"/developer", "/human", "/blog", "/lottery/lotto/stats", "/lottery/pension/records", "/a/b/c/d/e/f"}) {
            mvc.perform(get(path)).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void api_oauth_login_error_와_확장자_경로는_포워딩하지_않는다() throws Exception {
        for (String path : new String[]{"/api", "/api/pension/latest", "/oauth2/authorization/google", "/login/oauth2/code/google", "/error", "/assets/index-abc.js", "/favicon.ico", "/gif/CHEST_PRESS.gif", "/assets/sub/deep/file.js", "/a/b/c/d/e/f/g", "/blog/post.1/edit", "/lottery/lotto/stats/"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Test
    void POST_는_포워딩하지_않는다() throws Exception {
        mvc.perform(post("/lottery/lotto/stats")).andExpect(status().is4xxClientError());
    }

    @Test
    void isSpaRoute_판별() {
        assertEquals(true, SpaForwardController.isSpaRoute(req("GET", "/lottery/lotto/stats")));
        assertEquals(true, SpaForwardController.isSpaRoute(req("HEAD", "/human")));
        assertEquals(false, SpaForwardController.isSpaRoute(req("GET", "/")));
        assertEquals(false, SpaForwardController.isSpaRoute(req("POST", "/human")));
        assertEquals(false, SpaForwardController.isSpaRoute(req("GET", "/api/admin/login")));
        assertEquals(false, SpaForwardController.isSpaRoute(req("GET", "/oauth2/authorization/google")));
        assertEquals(false, SpaForwardController.isSpaRoute(req("GET", "/login/oauth2/code/google")));
        assertEquals(false, SpaForwardController.isSpaRoute(req("GET", "/assets/index-abc.js")));
        assertEquals(false, SpaForwardController.isSpaRoute(req("GET", "/error")));
        assertEquals(false, SpaForwardController.isSpaRoute(req("GET", "/a/b/c/d/e/f/g")));   // 최대 깊이(6) 초과
        assertEquals(false, SpaForwardController.isSpaRoute(req("GET", "/blog/post.1/edit"))); // 중간 세그먼트에 '.'
        assertEquals(false, SpaForwardController.isSpaRoute(req("GET", "/lottery/lotto/stats/"))); // 끝 슬래시
        assertEquals(true, SpaForwardController.isSpaRoute(req("GET", "/a/b/c/d/e/f")));
        // 접두사가 비슷한 일반 경로는 SPA 라우트로 취급
        assertEquals(true, SpaForwardController.isSpaRoute(req("GET", "/apiary")));
        assertEquals(true, SpaForwardController.isSpaRoute(req("GET", "/loginpage")));
    }

    private static MockHttpServletRequest req(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }
}
