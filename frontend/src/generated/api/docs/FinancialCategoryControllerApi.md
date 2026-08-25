# FinancialCategoryControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                                 | HTTP request                      | Description |
| -------------------------------------------------------------------------------------- | --------------------------------- | ----------- |
| [**getFinancialCategories**](FinancialCategoryControllerApi.md#getfinancialcategories) | **GET** /api/financial-categories |             |

## getFinancialCategories

> Array&lt;FinancialCategoryResponse&gt; getFinancialCategories(direction, activityId)

### Example

```ts
import { Configuration, FinancialCategoryControllerApi } from '';
import type { GetFinancialCategoriesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new FinancialCategoryControllerApi();

  const body = {
    // 'INCOME' | 'EXPENSE' (optional)
    direction: direction_example,
    // number (optional)
    activityId: 789,
  } satisfies GetFinancialCategoriesRequest;

  try {
    const data = await api.getFinancialCategories(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name           | Type                | Description | Notes                                                        |
| -------------- | ------------------- | ----------- | ------------------------------------------------------------ |
| **direction**  | `INCOME`, `EXPENSE` |             | [Optional] [Defaults to `undefined`] [Enum: INCOME, EXPENSE] |
| **activityId** | `number`            |             | [Optional] [Defaults to `undefined`]                         |

### Return type

[**Array&lt;FinancialCategoryResponse&gt;**](FinancialCategoryResponse.md)

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
