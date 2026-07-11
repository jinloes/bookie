# HealthControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                      | HTTP request        | Description |
| ------------------------------------------- | ------------------- | ----------- |
| [**health**](HealthControllerApi.md#health) | **GET** /api/health |             |

## health

> HealthResponse health()

### Example

```ts
import { Configuration, HealthControllerApi } from '';
import type { HealthRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new HealthControllerApi();

  try {
    const data = await api.health();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**HealthResponse**](HealthResponse.md)

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
