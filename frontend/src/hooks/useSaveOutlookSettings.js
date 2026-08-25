import { useQueryClient } from '@tanstack/react-query';
import { updateOutlookFolderSettings, updateOutlookMoveSettings } from '../api/index.js';
import { queryKeys } from '../queryKeys.js';

/** Saves watched-folder activity context and move settings as one UI action. */
export function useSaveOutlookSettings() {
  const queryClient = useQueryClient();

  const saveOutlookSettings = async ({ folderSettings, moveEnabled, moveDestinationFolderId }) => {
    await Promise.all([
      updateOutlookFolderSettings(folderSettings),
      updateOutlookMoveSettings(moveEnabled, moveDestinationFolderId),
    ]);
    queryClient.invalidateQueries({ queryKey: queryKeys.outlookFolderSettings });
    queryClient.invalidateQueries({ queryKey: queryKeys.outlookMoveSettings });
    queryClient.invalidateQueries({ queryKey: ['outlookRentalEmails'] });
  };

  return { saveOutlookSettings };
}
