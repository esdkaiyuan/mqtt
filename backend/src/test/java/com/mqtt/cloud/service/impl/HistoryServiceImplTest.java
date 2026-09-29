package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.dto.request.HistoryQueryDTO;
import com.mqtt.cloud.entity.HistoryRecord;
import com.mqtt.cloud.mapper.MessageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class HistoryServiceImplTest {

    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final HistoryServiceImpl service = new HistoryServiceImpl(messageMapper);

    @Test
    void getHistoryRecords_should_delegate_to_message_mapper_with_filters() {
        Page<HistoryRecord> expected = new Page<>(2, 10);
        when(messageMapper.pageHistoryQuery(any(), any(), eq(7L))).thenReturn(expected);

        HistoryQueryDTO dto = new HistoryQueryDTO();
        dto.setDeviceId(42L);
        dto.setTopic("device/sensor");
        dto.setStartTime("2026-09-01 00:00:00");
        dto.setEndTime("2026-09-30 23:59:59");
        dto.setPageNum(2);
        dto.setPageSize(10);

        IPage<HistoryRecord> result = service.getHistoryRecords(dto, 7L);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<Page<HistoryRecord>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<HistoryQueryDTO> dtoCaptor = ArgumentCaptor.forClass(HistoryQueryDTO.class);
        verify(messageMapper).pageHistoryQuery(pageCaptor.capture(), dtoCaptor.capture(), eq(7L));

        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);

        HistoryQueryDTO passed = dtoCaptor.getValue();
        assertThat(passed.getDeviceId()).isEqualTo(42L);
        assertThat(passed.getTopic()).isEqualTo("device/sensor");
        assertThat(passed.getStartTime()).isEqualTo("2026-09-01 00:00:00");
        assertThat(passed.getEndTime()).isEqualTo("2026-09-30 23:59:59");
    }
}