package com.agentinsight.service;

import com.agentinsight.dto.OnlineQualityReportDTO;

public interface OnlineQualityService {

    OnlineQualityReportDTO getReport(long startMs, long endMs, int page, int pageSize);
}
