# ReceiptDto

## Properties

| Name         | Type    |
| ------------ | ------- |
| `id`         | string  |
| `name`       | string  |
| `year`       | number  |
| `webUrl`     | string  |
| `uploadedAt` | string  |
| `expenseId`  | number  |
| `incomeId`   | number  |
| `pending`    | boolean |

## Example

```typescript
import type { ReceiptDto } from '';

// TODO: Update the object below with actual values
const example = {
  id: null,
  name: null,
  year: null,
  webUrl: null,
  uploadedAt: null,
  expenseId: null,
  incomeId: null,
  pending: null,
} satisfies ReceiptDto;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ReceiptDto;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
