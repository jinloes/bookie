# ProposedExpense

## Properties

| Name           | Type   |
| -------------- | ------ |
| `amount`       | number |
| `description`  | string |
| `date`         | string |
| `category`     | string |
| `propertyId`   | number |
| `propertyName` | string |
| `payerId`      | number |
| `payerName`    | string |

## Example

```typescript
import type { ProposedExpense } from '';

// TODO: Update the object below with actual values
const example = {
  amount: null,
  description: null,
  date: null,
  category: null,
  propertyId: null,
  propertyName: null,
  payerId: null,
  payerName: null,
} satisfies ProposedExpense;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ProposedExpense;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
