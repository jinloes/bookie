# ProposedTransaction

## Properties

| Name                      | Type    |
| ------------------------- | ------- |
| `direction`               | string  |
| `amount`                  | number  |
| `description`             | string  |
| `date`                    | string  |
| `activityId`              | number  |
| `activityName`            | string  |
| `ownerName`               | string  |
| `categoryId`              | number  |
| `categoryKey`             | string  |
| `categoryLabel`           | string  |
| `propertyId`              | number  |
| `propertyName`            | string  |
| `payerId`                 | number  |
| `counterpartyName`        | string  |
| `classificationAmbiguous` | boolean |

## Example

```typescript
import type { ProposedTransaction } from '';

// TODO: Update the object below with actual values
const example = {
  direction: null,
  amount: null,
  description: null,
  date: null,
  activityId: null,
  activityName: null,
  ownerName: null,
  categoryId: null,
  categoryKey: null,
  categoryLabel: null,
  propertyId: null,
  propertyName: null,
  payerId: null,
  counterpartyName: null,
  classificationAmbiguous: null,
} satisfies ProposedTransaction;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ProposedTransaction;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
