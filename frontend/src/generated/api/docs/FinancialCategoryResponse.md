# FinancialCategoryResponse

## Properties

| Name           | Type    |
| -------------- | ------- |
| `id`           | number  |
| `key`          | string  |
| `label`        | string  |
| `direction`    | string  |
| `taxTreatment` | string  |
| `taxLine`      | string  |
| `active`       | boolean |
| `system`       | boolean |

## Example

```typescript
import type { FinancialCategoryResponse } from '';

// TODO: Update the object below with actual values
const example = {
  id: null,
  key: null,
  label: null,
  direction: null,
  taxTreatment: null,
  taxLine: null,
  active: null,
  system: null,
} satisfies FinancialCategoryResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FinancialCategoryResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
