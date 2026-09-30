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

import java.util.List;

import io.github.devers2.s2util.core.S2Util;
import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2util.pagination.vo.S2SearchVO;

/**
 * s2's utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2023. 05. 31.
 */
public class S2PaginationInfo<T> {

    private S2SearchVO searchVO;
    private List<T> dataList;

    /** 처음 페이지 */
    private String firstPageLabel;
    /** 이전 페이지 */
    private String previousPageLabel;
    /** 현재 페이지 */
    private String currentPageLabel;
    /** 다른 페이지 */
    private String otherPageLabel;
    /** 다음 페이지 */
    private String nextPageLabel;
    /** 마지막 페이지 */
    private String lastPageLabel;

    /**
     * 전체 레코드 개수
     */
    private long totalRecordCount;

    public S2PaginationInfo(S2SearchVO searchVO, List<T> dataList, long totalRecordCount) {
        this.searchVO = searchVO;
        this.dataList = dataList;
        this.totalRecordCount = totalRecordCount;
        this.initPageLabels();
    }

    public S2PaginationInfo(S2SearchVO searchVO, List<T> dataList, int totalRecordCount) {
        this.searchVO = searchVO;
        this.dataList = dataList;
        this.totalRecordCount = totalRecordCount;
        this.initPageLabels();
    }

    public S2PaginationInfo(S2SearchVO searchVO, List<T> dataList, String totalRecordCountNm) {
        var totalRecordCount = totalRecordCountNm != null && !totalRecordCountNm.isBlank()
                && S2Util.isNotEmpty(dataList) ? S2Util.getValue(dataList.get(0), totalRecordCountNm, 0) : 0;
        this.searchVO = searchVO;
        this.dataList = dataList;
        this.totalRecordCount = totalRecordCount;
        this.initPageLabels();
    }

    /**
     * Pagination 의 Record 번호를 계산하여 가져온다
     *
     * @param paginationInfo  pagination 정보
     * @param recordNoPerPage 현재 페이지에서의 Record번호 (1, 2, 3...)
     * @param order           정렬(ASC|DESC)
     * @return Record 번호
     */
    public static long getRecordNo(S2PaginationInfo<?> paginationInfo, int recordNoPerPage, String order) {
        var resultRecordNo = 0L;
        if (paginationInfo != null) {
            var totalRecordCount = paginationInfo.getTotalRecordCount();
            var pageUnit = paginationInfo.getPageUnit();
            var pageNo = paginationInfo.getPageNo();
            var vOrder = S2Util.cast(order, "");

            if (vOrder != null && !vOrder.isBlank() && !"ASC".equalsIgnoreCase(vOrder)
                    && !"DESC".equalsIgnoreCase(vOrder)) {
                throw new S2RuntimeException("허용하지 않은 order값 입니다.");
            }

            if (vOrder != null && vOrder.equalsIgnoreCase("DESC")) {
                resultRecordNo = totalRecordCount + 1 - ((long) (pageNo - 1) * pageUnit + recordNoPerPage);
            } else {
                resultRecordNo = (long) (pageNo - 1) * pageUnit + recordNoPerPage;
            }
        }
        return resultRecordNo;
    }

    /**
     * 랜더링될 페이지 라벨을 설정한다.
     *
     * @apiNote
     *
     *          <pre>{@code
     * protected void initPageLabels() {
     *     this.setPageLabels(new String[] { 처음 페이지 라벨, 이전 페이지 라벨, 현재 페이지 라벨, 다른 페이지 라벨, 다음 페이지 라벨, 마지막 페이지 라벨 });
     * }
     * }</pre>
     *
     * @details
     *          <dl>
     *          <dd>라벨 변경이 필요하다면 S2PaginationInfo를 상속받아 해당 메서드만 재정의 한다.</dd>
     *          <dd>{0}: 페이지 이동 스크립트 (현재 페이지 라벨에서는 페이지 번호), {1}: 페이지 번호. 단순 치환이므로 따옴표를 이스케이프하지 않는다.</dd>
     *          </dl>
     */
    protected void initPageLabels() {
        this.setPageLabels(
                new String[] {
                        "<a href=\"#\" onclick=\"{0} return false;\" title=\"첫 페이지 이동\">[처음]</a>&#160;",
                        "<a href=\"#\" onclick=\"{0} return false;\" title=\"이전 페이지 이동\">[이전]</a>&#160;",
                        "<strong title=\"{0} 페이지(현재 페이지)\">{0}</strong>&#160;",
                        "<a href=\"#\" onclick=\"{0} return false;\" title=\"{1} 페이지 이동\">{1}</a>&#160;",
                        "<a href=\"#\" onclick=\"{0} return false;\" title=\"다음 페이지 이동\">[다음]</a>&#160;",
                        "<a href=\"#\" onclick=\"{0} return false;\" title=\"끝 페이지 이동\">[마지막]</a>&#160;"
                });
    }

    protected final void setPageLabels(String... pageLabels) {
        this.firstPageLabel = pageLabels[0];
        this.previousPageLabel = pageLabels[1];
        this.currentPageLabel = pageLabels[2];
        this.otherPageLabel = pageLabels[3];
        this.nextPageLabel = pageLabels[4];
        this.lastPageLabel = pageLabels[5];
    }

    public final String getFirstPageLabel() {
        return firstPageLabel;
    }

    public final String getPreviousPageLabel() {
        return previousPageLabel;
    }

    public final String getCurrentPageLabel() {
        return currentPageLabel;
    }

    public final String getOtherPageLabel() {
        return otherPageLabel;
    }

    public final String getNextPageLabel() {
        return nextPageLabel;
    }

    public final String getLastPageLabel() {
        return lastPageLabel;
    }

    public final long getTotalRecordCount() {
        return totalRecordCount;
    }

    /**
     * 남은 레코드 개수
     *
     * @return 남은 레코드 개수
     */
    public final long getRemainRecordCount() {
        var currentPageUnit = this.dataList != null ? this.dataList.size() : 0;
        var previousPageUnit = this.getPageNo() > 1 ? ((this.getPageNo() - 1) * this.getPageUnit()) : 0;
        return totalRecordCount - currentPageUnit - previousPageUnit;
    }

    public final int getPageUnit() {
        return this.searchVO.getPageUnit();
    }

    public final int getPageSize() {
        return this.searchVO.getPageSize();
    }

    public final int getPageNo() {
        return this.searchVO.getPageNo();
    }

    /**
     * 첫번째 페이지 번호
     */
    public final int getFirstPageNo() {
        return 1;
    }

    /**
     * 마지막 페이지 번호
     */
    public final int getLastPageNo() {
        return this.getTotalPageCount();
    }

    /**
     * 다음 페이지 번호
     */
    public final int getNextPageNo() {
        var nextPage = this.getPageNo() + 1;
        return Math.min(this.getLastPageNo(), nextPage);
    }

    /**
     * 전체 페이지 개수
     */
    public final int getTotalPageCount() {
        return (int) ((this.getTotalRecordCount() - 1) / this.getPageUnit() + 1);
    }

    /**
     * 현재 페이지 목록의 첫번째 페이지
     */
    public final int getFirstPageNoOnPageList() {
        return ((this.getPageNo() - 1) / this.getPageSize()) * this.getPageSize() + 1;
    }

    /**
     * 현재 페이지 목록의 마지막 페이지
     */
    public final int getLastPageNoOnPageList() {
        var lastPageNoOnPageList = (this.getFirstPageNoOnPageList() + this.getPageSize()) - 1;
        return Math.min(lastPageNoOnPageList, this.getTotalPageCount());
    }

    /**
     * 데이터 목록을 가져온다.
     *
     * @return 데이터 목록
     */
    public List<T> getDataList() {
        return this.dataList;
    }

    /**
     * 데이터 목록이 비어 있는지 확인한다.
     *
     * @return true: 데이터 목록이 비어 있음, false: 데이터 목록이 비어 있지 않음
     */
    public boolean isEmpty() {
        return this.dataList == null || this.dataList.isEmpty();
    }

    /**
     * 데이터 목록이 비어 있지 않은지 확인한다.
     *
     * @return true: 데이터 목록이 비어 있지 않음, false: 데이터 목록이 비어 있음
     */
    public boolean isNotEmpty() {
        return !this.isEmpty();
    }

}
