# ScheduleEReportResponse

## Properties

| Name           | Type                                                                   |
| -------------- | ---------------------------------------------------------------------- |
| `year`         | number                                                                 |
| `rentalIncome` | number                                                                 |
| `expenses`     | number                                                                 |
| `netIncome`    | number                                                                 |
| `activities`   | [Array&lt;ScheduleEActivityResponse&gt;](ScheduleEActivityResponse.md) |

## Example

```typescript
import type { ScheduleEReportResponse } from '';

// TODO: Update the object below with actual values
const example = {
  year: null,
  rentalIncome: null,
  expenses: null,
  netIncome: null,
  activities: null,
} satisfies ScheduleEReportResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ScheduleEReportResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
