# ActivityCashflowResponse

## Properties

| Name          | Type                                                      |
| ------------- | --------------------------------------------------------- |
| `activity`    | [FinancialActivityResponse](FinancialActivityResponse.md) |
| `income`      | number                                                    |
| `expenses`    | number                                                    |
| `netCashflow` | number                                                    |

## Example

```typescript
import type { ActivityCashflowResponse } from '';

// TODO: Update the object below with actual values
const example = {
  activity: null,
  income: null,
  expenses: null,
  netCashflow: null,
} satisfies ActivityCashflowResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ActivityCashflowResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
