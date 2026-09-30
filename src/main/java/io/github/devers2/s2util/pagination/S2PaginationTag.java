/**
 * S2 Support Library
 *
 * Copyright 2020 - 2026 devers2 (이승수, Daejeon, Korea)
 * Contact: eseungsu.dev@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For more information, please see the LICENSE file in the root directory.
 */
package io.github.devers2.s2util.pagination;

import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.TagSupport;


/**
 * s2's utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2023. 05. 31.
 */
public class S2PaginationTag extends TagSupport {

    private static final long serialVersionUID = 1431221408661801454L;

    private S2PaginationInfo<Object> paginationInfo;
    private String jsFunction;
    private String jsParam;

    /** A JavaScript function name, optionally namespaced (fn, app.list.go) | JavaScript 함수 이름 (네임스페이스 허용) */
    private static final Pattern JS_FUNCTION = Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*");
    /** Numbers are passed as JavaScript numbers, everything else as strings | 숫자는 숫자로, 그 외는 문자열로 전달 */
    private static final Pattern JS_NUMBER = Pattern.compile("-?\\d{1,15}(\\.\\d{1,15})?");

    @Override
    public int doEndTag() throws JspException {
        try {
            JspWriter out = pageContext.getOut();
            out.println(this.renderPagination(jsParam));
            return EVAL_PAGE;
        } catch (IOException e) {
            throw new JspException(e);
        }
    }

    /**
     * Pagination 을 랜더링한다.
     * <p>
     * 라벨의 {@code {0}}은 페이지 이동 스크립트({@code jsFunction(jsParams..., pageNo);}), {@code {1}}은 페이지 번호로 바뀐다(현재 페이지
     * 라벨은 둘 다 페이지 번호). 숫자가 아닌
     * {@code jsParams}는 JavaScript 문자열로 이스케이프되어 {@code onclick} 속성에 안전하게 들어간다.
     * </p>
     *
     * @param jsParams js 매개변수
     * @return Pagination 문자열
     * @throws IllegalArgumentException jsFunction 이 JavaScript 함수 이름이 아닐 때
     */
    public final String renderPagination(String... jsParams) {
        if (jsFunction == null || !JS_FUNCTION.matcher(jsFunction).matches()) {
            throw new IllegalArgumentException("jsFunction 은 JavaScript 함수 이름이어야 합니다: " + jsFunction);
        }
        var strBuff = new StringBuilder();

        var firstPageNo = this.paginationInfo.getFirstPageNo();
        var firstPageNoOnPageList = this.paginationInfo.getFirstPageNoOnPageList();
        var totalPageCount = this.paginationInfo.getTotalPageCount();
        // 페이지 "묶음(윈도우)" 이동 판단에는 페이지당 레코드 수(pageUnit)가 아니라 페이지 목록에
        // 게시되는 페이지 건수(pageSize)를 써야 한다. getFirstPageNoOnPageList()/
        // getLastPageNoOnPageList() 도 pageSize 기준으로 윈도우를 계산한다.
        var pageSize = this.paginationInfo.getPageSize();
        var lastPageNoOnPageList = this.paginationInfo.getLastPageNoOnPageList();
        var pageNo = this.paginationInfo.getPageNo();
        var lastPageNo = this.paginationInfo.getLastPageNo();

        // '<a href="#" onclick="{0} return false;">' → '<a href="#" onclick="fn_action('list', 3); return false;">'
        var jsArgs = new StringBuilder();
        if (jsParams != null) {
            for (var param : jsParams) {
                if (param == null) {
                    continue;
                }
                jsArgs.append(JS_NUMBER.matcher(param).matches() ? param : jsString(param)).append(", ");
            }
        }
        var jsCallPrefix = this.jsFunction + "(" + jsArgs;

        if (totalPageCount > pageSize) {
            var previous = firstPageNoOnPageList > pageSize ? firstPageNoOnPageList - 1 : firstPageNo;
            strBuff.append(link(paginationInfo.getFirstPageLabel(), jsCallPrefix, firstPageNo));
            strBuff.append(link(paginationInfo.getPreviousPageLabel(), jsCallPrefix, previous));
        }
        for (int i = firstPageNoOnPageList; i <= lastPageNoOnPageList; i++) {
            if (i == pageNo) {
                // The current page is not a link: {0} is the page number | 현재 페이지는 링크가 아니므로 {0}이 페이지 번호
                var page = Integer.toString(i);
                strBuff.append(paginationInfo.getCurrentPageLabel().replace("{0}", page).replace("{1}", page));
            } else {
                strBuff.append(link(paginationInfo.getOtherPageLabel(), jsCallPrefix, i));
            }
        }
        if (totalPageCount > pageSize) {
            var next = lastPageNoOnPageList < totalPageCount ? firstPageNoOnPageList + pageSize : lastPageNo;
            strBuff.append(link(paginationInfo.getNextPageLabel(), jsCallPrefix, next));
            strBuff.append(link(paginationInfo.getLastPageLabel(), jsCallPrefix, lastPageNo));
        }
        return strBuff.toString();
    }

    /** Plain substitution, so quotes and braces in labels need no MessageFormat escaping | 단순 치환이라 라벨의 따옴표·중괄호에 MessageFormat 이스케이프가 필요 없음 */
    private static String link(String label, String jsCallPrefix, int pageNo) {
        var page = Integer.toString(pageNo);
        return label.replace("{0}", jsCallPrefix + page + ");").replace("{1}", page);
    }

    /**
     * A single-quoted JavaScript string safe inside a double-quoted HTML attribute: everything but letters, digits
     * and spaces becomes a unicode escape | 큰따옴표 HTML 속성 안에서도 안전한 작은따옴표 JS 문자열 (영문·숫자·공백 외에는 유니코드 이스케이프)
     */
    static String jsString(String value) {
        var sb = new StringBuilder(value.length() + 2).append('\'');
        for (var ch : value.toCharArray()) {
            if (Character.isLetterOrDigit(ch) || ch == ' ' || ch == '_' || ch == '-' || ch == '.') {
                sb.append(ch);
            } else {
                sb.append(String.format("\\u%04x", (int) ch));
            }
        }
        return sb.append('\'').toString();
    }

    public void setPaginationInfo(S2PaginationInfo<Object> paginationInfo) {
        this.paginationInfo = paginationInfo;
    }

    public void setJsFunction(String jsFunction) {
        this.jsFunction = jsFunction;
    }

    public void setJsParam(String jsParam) {
        this.jsParam = jsParam;
    }

}
