package com.android.data;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import org.codehaus.jackson.map.ObjectMapper;
import org.ektorp.CouchDbConnector;
import org.ektorp.DbAccessException;
import org.ektorp.ViewQuery;
import org.ektorp.ViewResult;
import org.ektorp.android.util.ChangesFeedAsyncTask;
import org.ektorp.android.util.CouchbaseViewListAdapter;
import org.ektorp.android.util.EktorpAsyncTask;
import org.ektorp.changes.ChangesCommand;
import org.ektorp.changes.DocumentChange;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;

public abstract class DataListAdapter<T extends Document> extends CouchbaseViewListAdapter {
    private final int layout;
    private static ObjectMapper mapper = new ObjectMapper();

    public DataListAdapter(Repository<T> repository, ViewQuery viewQuery, int layout, boolean followChanges) {
        super(repository.getConnector(), viewQuery, followChanges);
        this.layout = layout;
    }

    public DataListAdapter(Repository<T> repository, String viewName, int layout, boolean followChanges) {
        this(repository, repository.buildViewQuery(viewName), layout, followChanges);
    }

    @Override
    public ViewResult.Row getRow(int position) {
        return listRows.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        if (convertView == null) {
            view = newView(parent);
        } else {
            view = convertView;
        }
        populateView(view, getItem(position), position);
        return view;
    }

    @Override
    public T getItem(int position) {
        try {
            ViewResult.Row row = getRow(position);
            return mapper.readValue(row.getValueAsNode(), genericDocumentClass());
        } catch (IOException e) {
            Log.e(getClass().getName(), "Not able to de-serialize");
            return null;
        }
    }

    private View newView(ViewGroup parent) {
        LayoutInflater inflater = getInflater(parent);
        return inflater.inflate(layout, parent, false);
    }

    private LayoutInflater getInflater(ViewGroup parent) {
        return (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @SuppressWarnings("unchecked")
    private Class<T> genericDocumentClass() {
        ParameterizedType parameterizedType = (ParameterizedType) getClass().getGenericSuperclass();
        return (Class<T>) parameterizedType.getActualTypeArguments()[0];
    }

    // Fix to run the changes watcher in ThreadPool, so that they are alive - START

    protected ListChangesAsyncTask couchChangesAsyncTask;

    @Override
    protected void updateListItems() {
        if (updateListItemsTask == null) {

            updateListItemsTask = new EktorpAsyncTask() {

                protected ViewResult viewResult;

                @Override
                protected void doInBackground() {
                    viewResult = couchDbConnector.queryView(viewQuery);
                }

                protected void onSuccess() {
                    if (viewResult != null) {
                        lastUpdateView = viewResult.getUpdateSeq();
                        listRows = viewResult.getRows();
                        notifyDataSetChanged();
                    }
                    updateListItemsTask = null;

                    if (couchChangesAsyncTask == null && followChanges) {
                        ChangesCommand changesCmd = new ChangesCommand.Builder().since(lastUpdateView)
                                .includeDocs(false)
                                .continuous(true)
                                .heartbeat(5000)
                                .build();

                        couchChangesAsyncTask = new ListChangesAsyncTask(couchDbConnector, changesCmd);
                        couchChangesAsyncTask.execute();
                    }

                    if (lastUpdateChangesFeed > lastUpdateView) {
                        updateListItems();
                    }

                }

                @Override
                protected void onDbAccessException(
                        DbAccessException dbAccessException) {
                    handleViewAsyncTaskDbAccessException(dbAccessException);
                }

            };

            updateListItemsTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private class ListChangesAsyncTask extends ChangesFeedAsyncTask {

        public ListChangesAsyncTask(CouchDbConnector couchDbConnector,
                                    ChangesCommand changesCommand) {
            super(couchDbConnector, changesCommand);
        }

        @Override
        protected void handleDocumentChange(DocumentChange change) {
            lastUpdateChangesFeed = change.getSequence();
            updateListItems();
        }

        @Override
        protected void onDbAccessException(DbAccessException dbAccessException) {
            handleChangesAsyncTaskDbAccessException(dbAccessException);
        }

    }

    // END

    abstract public void populateView(View view, T document, int position);
}