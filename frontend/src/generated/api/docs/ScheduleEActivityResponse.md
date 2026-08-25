# ScheduleEActivityResponse

## Properties

| Name           | Type                                                           |
| -------------- | -------------------------------------------------------------- |
| `activity`     | [FinancialActivityResponse](FinancialActivityResponse.md)      |
| `rentalIncome` | number                                                         |
| `expenses`     | number                                                         |
| `netIncome`    | number                                                         |
| `categories`   | [Array&lt;CategoryTotalResponse&gt;](CategoryTotalResponse.md) |

## Example

```typescript
import type { ScheduleEActivityResponse } from '';

// TODO: Update the object below with actual values
const example = {
  activity: null,
  rentalIncome: null,
  expenses: null,
  netIncome: null,
  categories: null,
} satisfies ScheduleEActivityResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ScheduleEActivityResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
