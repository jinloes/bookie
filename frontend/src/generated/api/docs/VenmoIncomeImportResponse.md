# VenmoIncomeImportResponse

## Properties

| Name                   | Type   |
| ---------------------- | ------ |
| `totalRows`            | number |
| `importedRows`         | number |
| `skippedSenderRows`    | number |
| `skippedOutgoingRows`  | number |
| `skippedDuplicateRows` | number |
| `skippedInvalidRows`   | number |
| `senderFilter`         | string |
| `propertyName`         | string |

## Example

```typescript
import type { VenmoIncomeImportResponse } from '';

// TODO: Update the object below with actual values
const example = {
  totalRows: null,
  importedRows: null,
  skippedSenderRows: null,
  skippedOutgoingRows: null,
  skippedDuplicateRows: null,
  skippedInvalidRows: null,
  senderFilter: null,
  propertyName: null,
} satisfies VenmoIncomeImportResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as VenmoIncomeImportResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
