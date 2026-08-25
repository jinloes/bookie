# ReportControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                              | HTTP request                    | Description |
| ------------------------------------------------------------------- | ------------------------------- | ----------- |
| [**getCashflowReport**](ReportControllerApi.md#getcashflowreport)   | **GET** /api/reports/cashflow   |             |
| [**getScheduleEReport**](ReportControllerApi.md#getscheduleereport) | **GET** /api/reports/schedule-e |             |

## getCashflowReport

> CashflowSummaryResponse getCashflowReport(from, to, ownerId, activityId)

### Example

```ts
import { Configuration, ReportControllerApi } from '';
import type { GetCashflowReportRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ReportControllerApi();

  const body = {
    // string
    from: 2013 - 10 - 20,
    // string
    to: 2013 - 10 - 20,
    // number (optional)
    ownerId: 789,
    // number (optional)
    activityId: 789,
  } satisfies GetCashflowReportRequest;

  try {
    const data = await api.getCashflowReport(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name           | Type     | Description | Notes                                |
| -------------- | -------- | ----------- | ------------------------------------ |
| **from**       | `string` |             | [Defaults to `undefined`]            |
| **to**         | `string` |             | [Defaults to `undefined`]            |
| **ownerId**    | `number` |             | [Optional] [Defaults to `undefined`] |
| **activityId** | `number` |             | [Optional] [Defaults to `undefined`] |

### Return type

[**CashflowSummaryResponse**](CashflowSummaryResponse.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getScheduleEReport

> ScheduleEReportResponse getScheduleEReport(year, ownerId, activityId)

### Example

```ts
import { Configuration, ReportControllerApi } from '';
import type { GetScheduleEReportRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ReportControllerApi();

  const body = {
    // number
    year: 56,
    // number (optional)
    ownerId: 789,
    // number (optional)
    activityId: 789,
  } satisfies GetScheduleEReportRequest;

  try {
    const data = await api.getScheduleEReport(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name           | Type     | Description | Notes                                |
| -------------- | -------- | ----------- | ------------------------------------ |
| **year**       | `number` |             | [Defaults to `undefined`]            |
| **ownerId**    | `number` |             | [Optional] [Defaults to `undefined`] |
| **activityId** | `number` |             | [Optional] [Defaults to `undefined`] |

### Return type

[**ScheduleEReportResponse**](ScheduleEReportResponse.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
