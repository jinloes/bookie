# SaveInboxItemRequest

## Properties

| Name                | Type   |
| ------------------- | ------ |
| `direction`         | string |
| `amount`            | number |
| `date`              | string |
| `description`       | string |
| `activityId`        | number |
| `neutralCategoryId` | number |
| `counterpartyId`    | number |
| `sourceLabel`       | string |

## Example

```typescript
import type { SaveInboxItemRequest } from '';

// TODO: Update the object below with actual values
const example = {
  direction: null,
  amount: null,
  date: null,
  description: null,
  activityId: null,
  neutralCategoryId: null,
  counterpartyId: null,
  sourceLabel: null,
} satisfies SaveInboxItemRequest;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SaveInboxItemRequest;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
