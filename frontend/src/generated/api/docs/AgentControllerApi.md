# AgentControllerApi

All URIs are relative to *http://localhost:48763*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**submitExpense**](AgentControllerApi.md#submitexpense) | **POST** /api/agent/expense |  |



## submitExpense

> AgentResponse submitExpense(requestBody)



### Example

```ts
import {
  Configuration,
  AgentControllerApi,
} from '';
import type { SubmitExpenseRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new AgentControllerApi();

  const body = {
    // { [key: string]: string; }
    requestBody: Object,
  } satisfies SubmitExpenseRequest;

  try {
    const data = await api.submitExpense(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **requestBody** | `{ [key: string]: string; }` |  | |

### Return type

[**AgentResponse**](AgentResponse.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

