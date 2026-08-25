# CashflowSummaryResponse

## Properties

| Name            | Type                                                                 |
| --------------- | -------------------------------------------------------------------- |
| `from`          | string                                                               |
| `to`            | string                                                               |
| `totalIncome`   | number                                                               |
| `totalExpenses` | number                                                               |
| `netCashflow`   | number                                                               |
| `activities`    | [Array&lt;ActivityCashflowResponse&gt;](ActivityCashflowResponse.md) |

## Example

```typescript
import type { CashflowSummaryResponse } from '';

// TODO: Update the object below with actual values
const example = {
  from: null,
  to: null,
  totalIncome: null,
  totalExpenses: null,
  netCashflow: null,
  activities: null,
} satisfies CashflowSummaryResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CashflowSummaryResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
