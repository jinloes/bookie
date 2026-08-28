# LegacyClassificationHistoryControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                                     | HTTP request                     | Description |
| ------------------------------------------------------------------------------------------ | -------------------------------- | ----------- |
| [**getPayerKeywords**](LegacyClassificationHistoryControllerApi.md#getpayerkeywords)       | **GET** /api/payers/keywords     |             |
| [**getPropertyKeywords**](LegacyClassificationHistoryControllerApi.md#getpropertykeywords) | **GET** /api/properties/keywords |             |

## getPayerKeywords

> Array&lt;EmailKeywordPayerHistory&gt; getPayerKeywords()

### Example

```ts
import { Configuration, LegacyClassificationHistoryControllerApi } from '';
import type { GetPayerKeywordsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new LegacyClassificationHistoryControllerApi();

  try {
    const data = await api.getPayerKeywords();
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

[**Array&lt;EmailKeywordPayerHistory&gt;**](EmailKeywordPayerHistory.md)

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

## getPropertyKeywords

> Array&lt;EmailKeywordPropertyHistory&gt; getPropertyKeywords()

### Example

```ts
import { Configuration, LegacyClassificationHistoryControllerApi } from '';
import type { GetPropertyKeywordsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new LegacyClassificationHistoryControllerApi();

  try {
    const data = await api.getPropertyKeywords();
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

[**Array&lt;EmailKeywordPropertyHistory&gt;**](EmailKeywordPropertyHistory.md)

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
