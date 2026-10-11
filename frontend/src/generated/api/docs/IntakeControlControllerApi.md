# IntakeControlControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                           | HTTP request                  | Description |
| ---------------------------------------------------------------- | ----------------------------- | ----------- |
| [**drainIntake**](IntakeControlControllerApi.md#drainintake)     | **POST** /api/intake/drain    |             |
| [**runAutoImport**](IntakeControlControllerApi.md#runautoimport) | **POST** /api/auto-import/run |             |

## drainIntake

> drainIntake()

### Example

```ts
import { Configuration, IntakeControlControllerApi } from '';
import type { DrainIntakeRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new IntakeControlControllerApi();

  try {
    const data = await api.drainIntake();
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

`void` (Empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **202**     | Accepted              | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## runAutoImport

> AutoImportRunResponse runAutoImport()

### Example

```ts
import { Configuration, IntakeControlControllerApi } from '';
import type { RunAutoImportRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new IntakeControlControllerApi();

  try {
    const data = await api.runAutoImport();
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

[**AutoImportRunResponse**](AutoImportRunResponse.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
