/*
 * 版权所有 (C) 2007 Android 开源项目
 *
 * 根据 Apache 许可证 2.0 版（“许可证”）授权；除非遵守许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则根据许可证分发的软件按“原样”分发，
 * 不附带任何明示或暗示的保证或条件。有关许可的具体语言，请参阅许可证。
 */

package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentProvider.PipeDataWriter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.LiveFolders;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

/**
 * 提供对笔记数据库的访问。每条笔记都有标题、笔记内容、创建日期和修改日期。
 */
public class NotePadProvider extends ContentProvider implements PipeDataWriter<Cursor> {
    // 用于调试和日志记录
    private static final String TAG = "NotePadProvider";

    /**
     * 提供者使用的底层数据存储的数据库
     */
    private static final String DATABASE_NAME = "note_pad.db";

    /**
     * 数据库版本
     */
    private static final int DATABASE_VERSION = 2;

    /**
     * 用于从数据库中选择列的投影映射
     */
    private static HashMap<String, String> sNotesProjectionMap;

    /**
     * 用于从数据库中选择列的投影映射
     */
    private static HashMap<String, String> sLiveFolderProjectionMap;

    /**
     * 常规笔记的有趣列的标准投影。
     */
    private static final String[] READ_NOTE_PROJECTION = new String[]{
            NotePad.Notes._ID,               // 投影位置 0，笔记的 ID
            NotePad.Notes.COLUMN_NAME_NOTE,  // 投影位置 1，笔记的内容
            NotePad.Notes.COLUMN_NAME_TITLE, // 投影位置 2，笔记的标题
    };
    private static final int READ_NOTE_NOTE_INDEX = 1;
    private static final int READ_NOTE_TITLE_INDEX = 2;

    /*
     * Uri 匹配器使用的常量，根据传入 URI 的模式选择操作
     */
    // 传入的 URI 匹配 Notes URI 模式
    private static final int NOTES = 1;

    // 传入的 URI 匹配 Note ID URI 模式
    private static final int NOTE_ID = 2;

    // 传入的 URI 匹配 Live Folder URI 模式
    private static final int LIVE_FOLDER_NOTES = 3;

    /**
     * UriMatcher 实例
     */
    private static final UriMatcher sUriMatcher;

    // 新的 DatabaseHelper 的句柄。
    private DatabaseHelper mOpenHelper;

    // 搜索
    private static final int NOTES_SEARCH = 4;

    /**
     * 一个块，用于实例化和设置静态对象
     */

    static {

        /*
         * 创建并初始化 URI 匹配器
         */
        // 创建一个新实例
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // 添加一个模式，将以 "notes" 结尾的 URI 路由到 NOTES 操作
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes", NOTES);

        // 添加一个模式，将以 "notes" 加上一个整数结尾的 URI 路由到 note ID 操作
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);

        // 添加一个模式，将以 live_folders/notes 结尾的 URI 路由到 live folder 操作
        sUriMatcher.addURI(NotePad.AUTHORITY, "live_folders/notes", LIVE_FOLDER_NOTES);

        // 新增搜索 URI
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/search", NOTES_SEARCH);
        /*
         * 创建并初始化一个返回所有列的投影映射
         */

        // 创建一个新的投影映射实例。给定一个字符串，映射返回一个列名。两者通常相等。
        sNotesProjectionMap = new HashMap<String, String>();

        // 将字符串 "_ID" 映射到列名 "_ID"
        sNotesProjectionMap.put(NotePad.Notes._ID, NotePad.Notes._ID);

        // 将 "title" 映射到 "title"
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_TITLE);

        // 将 "note" 映射到 "note"
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.COLUMN_NAME_NOTE);

        // 将 "created" 映射到 "created"
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                NotePad.Notes.COLUMN_NAME_CREATE_DATE);

        // 将 "modified" 映射到 "modified"
        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);

        /*
         * 创建并初始化处理 Live Folders 的投影映射
         */

        // 创建一个新的投影映射实例
        sLiveFolderProjectionMap = new HashMap<String, String>();

        // 将 "_ID" 映射到 "ID AS _ID" 用于 live folder
        sLiveFolderProjectionMap.put(LiveFolders._ID, NotePad.Notes._ID + " AS " + LiveFolders._ID);

        // 将 "NAME" 映射到 "title AS NAME"
        sLiveFolderProjectionMap.put(LiveFolders.NAME, NotePad.Notes.COLUMN_NAME_TITLE + " AS " +
                LiveFolders.NAME);
    }

    /**
     * 这个类帮助打开、创建和升级数据库文件。为了测试目的，设置为包可见性。
     */
    static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {

            // 调用超类构造函数，请求默认的游标工厂。
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        /**
         * 使用 NotePad 类中的表名和列名创建底层数据库。
         */
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
                    + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
                    + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
                    + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
                    + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                    + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER"
                    + ");");
        }

        /**
         * 演示提供者必须考虑当底层数据存储发生变化时会发生什么。在这个示例中，通过销毁现有数据来升级数据库。
         * 真实的应用程序应该就地升级数据库。
         */

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            // Logs that the database is being upgraded
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");

            // Kills the table and existing data
            db.execSQL("DROP TABLE IF EXISTS notes");

            // Recreates the database with a new version
            onCreate(db);
        }
    }

    /**
     * Initializes the provider by creating a new DatabaseHelper. onCreate() is called
     * automatically when Android creates the provider in response to a resolver request from a
     * client.
     */
    @Override
    public boolean onCreate() {

        // Creates a new helper object. Note that the database itself isn't opened until
        // something tries to access it, and it's only created if it doesn't already exist.
        mOpenHelper = new DatabaseHelper(getContext());

        // Assumes that any failures will be reported by a thrown exception.
        return true;
    }

    /**
     * This method is called when a client calls
     * {@link android.content.ContentResolver#query(Uri, String[], String, String[], String)}.
     * Queries the database and returns a cursor containing the results.
     *
     * @return A cursor containing the results of the query. The cursor exists but is empty if
     * the query returns no results or an exception occurs.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
   @Override
public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    qb.setTables(NotePad.Notes.TABLE_NAME);

    switch (sUriMatcher.match(uri)) {
        case NOTES:
            qb.setProjectionMap(sNotesProjectionMap);
            break;
        case NOTE_ID:
            qb.setProjectionMap(sNotesProjectionMap);
            qb.appendWhere(NotePad.Notes._ID + "=" + uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION));
            break;
        case LIVE_FOLDER_NOTES:
            qb.setProjectionMap(sLiveFolderProjectionMap);
            break;
        case NOTES_SEARCH:
            qb.setProjectionMap(sNotesProjectionMap);
            if (selection != null && !selection.isEmpty()) {
                qb.appendWhere(selection);
            }
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
    }

    String orderBy;
    if (TextUtils.isEmpty(sortOrder)) {
        orderBy = NotePad.Notes.DEFAULT_SORT_ORDER;
    } else {
        orderBy = sortOrder;
    }

    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
    c.setNotificationUri(getContext().getContentResolver(), uri);
    return c;
}

    /**
     * This is called when a client calls {@link android.content.ContentResolver#getType(Uri)}.
     * Returns the MIME data type of the URI given as a parameter.
     *
     * @param uri The URI whose MIME type is desired.
     * @return The MIME type of the URI.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public String getType(Uri uri) {

        /**
         * Chooses the MIME type based on the incoming URI pattern
         */
        switch (sUriMatcher.match(uri)) {

            // If the pattern is for notes or live folders, returns the general content type.
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return NotePad.Notes.CONTENT_TYPE;

            // If the pattern is for note IDs, returns the note ID content type.
            case NOTE_ID:
                return NotePad.Notes.CONTENT_ITEM_TYPE;

            // If the URI pattern doesn't match any permitted patterns, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

//BEGIN_INCLUDE(stream)
    /**
     * This describes the MIME types that are supported for opening a note
     * URI as a stream.
     */
    static ClipDescription NOTE_STREAM_TYPES = new ClipDescription(null,
            new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN});

    /**
     * Returns the types of available data streams.  URIs to specific notes are supported.
     * The application can convert such a note to a plain text stream.
     *
     * @param uri            the URI to analyze
     * @param mimeTypeFilter The MIME type to check for. This method only returns a data stream
     *                       type for MIME types that match the filter. Currently, only text/plain MIME types match.
     * @return a data stream MIME type. Currently, only text/plan is returned.
     * @throws IllegalArgumentException if the URI pattern doesn't match any supported patterns.
     */
    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        /**
         *  Chooses the data stream type based on the incoming URI pattern.
         */
        switch (sUriMatcher.match(uri)) {

            // If the pattern is for notes or live folders, return null. Data streams are not
            // supported for this type of URI.
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return null;

            // If the pattern is for note IDs and the MIME filter is text/plain, then return
            // text/plain
            case NOTE_ID:
                return NOTE_STREAM_TYPES.filterMimeTypes(mimeTypeFilter);

            // If the URI pattern doesn't match any permitted patterns, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }


    /**
     * Returns a stream of data for each supported stream type. This method does a query on the
     * incoming URI, then uses
     * {@link android.content.ContentProvider#openPipeHelper(Uri, String, Bundle, Object,
     * PipeDataWriter)} to start another thread in which to convert the data into a stream.
     *
     * @param uri            The URI pattern that points to the data stream
     * @param mimeTypeFilter A String containing a MIME type. This method tries to get a stream of
     *                       data with this MIME type.
     * @param opts           Additional options supplied by the caller.  Can be interpreted as
     *                       desired by the content provider.
     * @return AssetFileDescriptor A handle to the file.
     * @throws FileNotFoundException if there is no file associated with the incoming URI.
     */
    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {

        // Checks to see if the MIME type filter matches a supported MIME type.
        String[] mimeTypes = getStreamTypes(uri, mimeTypeFilter);

        // If the MIME type is supported
        if (mimeTypes != null) {

            // Retrieves the note for this URI. Uses the query method defined for this provider,
            // rather than using the database query method.
            Cursor c = query(
                    uri,                    // The URI of a note
                    READ_NOTE_PROJECTION,   // Gets a projection containing the note's ID, title,
                    // and contents
                    null,                   // No WHERE clause, get all matching records
                    null,                   // Since there is no WHERE clause, no selection criteria
                    null                    // Use the default sort order (modification date,
                    // descending
            );


            // If the query fails or the cursor is empty, stop
            if (c == null || !c.moveToFirst()) {

                // If the cursor is empty, simply close the cursor and return
                if (c != null) {
                    c.close();
                }

                // If the cursor is null, throw an exception
                throw new FileNotFoundException("Unable to query " + uri);
            }

            // Start a new thread that pipes the stream data back to the caller.
            return new AssetFileDescriptor(
                    openPipeHelper(uri, mimeTypes[0], opts, c, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        // If the MIME type is not supported, return a read-only handle to the file.
        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }

    /**
     * Implementation of {@link android.content.ContentProvider.PipeDataWriter}
     * to perform the actual work of converting the data in one of cursors to a
     * stream of data for the client to read.
     */
    @Override
    public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
                                Bundle opts, Cursor c) {
        // We currently only support conversion-to-text from a single note entry,
        // so no need for cursor data type checking here.
        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));
            pw.println(c.getString(READ_NOTE_TITLE_INDEX));
            pw.println("");
            pw.println(c.getString(READ_NOTE_NOTE_INDEX));
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Ooops", e);
        } finally {
            c.close();
            if (pw != null) {
                pw.flush();
            }
            try {
                fout.close();
            } catch (IOException e) {
            }
        }
    }
//END_INCLUDE(stream)

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#insert(Uri, ContentValues)}.
     * Inserts a new row into the database. This method sets up default values for any
     * columns that are not included in the incoming map.
     * If rows were inserted, then listeners are notified of the change.
     *
     * @return The row ID of the inserted row.
     * @throws SQLException if the insertion fails.
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        // Validates the incoming URI. Only the full provider URI is allowed for inserts.
        if (sUriMatcher.match(uri) != NOTES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // A map to hold the new record's values.
        ContentValues values;

        // If the incoming values map is not null, uses it for the new values.
        if (initialValues != null) {
            values = new ContentValues(initialValues);

        } else {
            // Otherwise, create a new value map
            values = new ContentValues();
        }

        // Gets the current system time in milliseconds
        Long now = Long.valueOf(System.currentTimeMillis());

        // If the values map doesn't contain the creation date, sets the value to the current time.
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
        }

        // If the values map doesn't contain the modification date, sets the value to the current
        // time.
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        }

        // If the values map doesn't contain a title, sets the value to the default title.
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE) == false) {
            Resources r = Resources.getSystem();
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, r.getString(android.R.string.untitled));
        }

        // If the values map doesn't contain note text, sets the value to an empty string.
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
        }

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        // Performs the insert and returns the ID of the new note.
        long rowId = db.insert(
                NotePad.Notes.TABLE_NAME,        // The table to insert into.
                NotePad.Notes.COLUMN_NAME_NOTE,  // A hack, SQLite sets this column value to null
                // if values is empty.
                values                           // A map of column names, and the values to insert
                // into the columns.
        );

        // If the insert succeeded, the row ID exists.
        if (rowId > 0) {
            // Creates a URI with the note ID pattern and the new row ID appended to it.
            Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId);

            // Notifies observers registered against this provider that the data changed.
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }

        // If the insert didn't succeed, then the rowID is <= 0. Throws an exception.
        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#delete(Uri, String, String[])}.
     * Deletes records from the database. If the incoming URI matches the note ID URI pattern,
     * this method deletes the one record specified by the ID in the URI. Otherwise, it deletes a
     * a set of records. The record or records must also match the input selection criteria
     * specified by where and whereArgs.
     * <p>
     * If rows were deleted, then listeners are notified of the change.
     *
     * @return If a "where" clause is used, the number of rows affected is returned, otherwise
     * 0 is returned. To delete all rows and get a row count, use "1" as the where clause.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;

        int count;

        // Does the delete based on the incoming URI pattern.
        switch (sUriMatcher.match(uri)) {

            // If the incoming pattern matches the general pattern for notes, does a delete
            // based on the incoming "where" columns and arguments.
            case NOTES:
                count = db.delete(
                        NotePad.Notes.TABLE_NAME,  // The database table name
                        where,                     // The incoming where clause column names
                        whereArgs                  // The incoming where clause values
                );
                break;

            // If the incoming URI matches a single note ID, does the delete based on the
            // incoming data, but modifies the where clause to restrict it to the
            // particular note ID.
            case NOTE_ID:
                /*
                 * Starts a final WHERE clause by restricting it to the
                 * desired note ID.
                 */
                finalWhere =
                        NotePad.Notes._ID +                              // The ID column name
                                " = " +                                          // test for equality
                                uri.getPathSegments().                           // the incoming note ID
                                        get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                // If there were additional selection criteria, append them to the final
                // WHERE clause
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Performs the delete.
                count = db.delete(
                        NotePad.Notes.TABLE_NAME,  // The database table name.
                        finalWhere,                // The final WHERE clause
                        whereArgs                  // The incoming where clause values.
                );
                break;

            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /*Gets a handle to the content resolver object for the current context, and notifies it
         * that the incoming URI changed. The object passes this along to the resolver framework,
         * and observers that have registered themselves for the provider are notified.
         */
        getContext().getContentResolver().notifyChange(uri, null);

        // Returns the number of rows deleted.
        return count;
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#update(Uri, ContentValues, String, String[])}
     * Updates records in the database. The column names specified by the keys in the values map
     * are updated with new data specified by the values in the map. If the incoming URI matches the
     * note ID URI pattern, then the method updates the one record specified by the ID in the URI;
     * otherwise, it updates a set of records. The record or records must match the input
     * selection criteria specified by where and whereArgs.
     * If rows were updated, then listeners are notified of the change.
     *
     * @param uri       The URI pattern to match and update.
     * @param values    A map of column names (keys) and new values (values).
     * @param where     An SQL "WHERE" clause that selects records based on their column values. If this
     *                  is null, then all records that match the URI pattern are selected.
     * @param whereArgs An array of selection criteria. If the "where" param contains value
     *                  placeholders ("?"), then each placeholder is replaced by the corresponding element in the
     *                  array.
     * @return The number of rows updated.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;

        // Does the update based on the incoming URI pattern
        switch (sUriMatcher.match(uri)) {

            // If the incoming URI matches the general notes pattern, does the update based on
            // the incoming data.
            case NOTES:

                // Does the update and returns the number of rows updated.
                count = db.update(
                        NotePad.Notes.TABLE_NAME, // The database table name.
                        values,                   // A map of column names and new values to use.
                        where,                    // The where clause column names.
                        whereArgs                 // The where clause column values to select on.
                );
                break;

            // If the incoming URI matches a single note ID, does the update based on the incoming
            // data, but modifies the where clause to restrict it to the particular note ID.
            case NOTE_ID:
                // From the incoming URI, get the note ID
                String noteId = uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);

                /*
                 * Starts creating the final WHERE clause by restricting it to the incoming
                 * note ID.
                 */
                finalWhere =
                        NotePad.Notes._ID +                              // The ID column name
                                " = " +                                          // test for equality
                                uri.getPathSegments().                           // the incoming note ID
                                        get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                // If there were additional selection criteria, append them to the final WHERE
                // clause
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }


                // Does the update and returns the number of rows updated.
                count = db.update(
                        NotePad.Notes.TABLE_NAME, // The database table name.
                        values,                   // A map of column names and new values to use.
                        finalWhere,               // The final WHERE clause to use
                        // placeholders for whereArgs
                        whereArgs                 // The where clause column values to select on, or
                        // null if the values are in the where argument.
                );
                break;
            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /*Gets a handle to the content resolver object for the current context, and notifies it
         * that the incoming URI changed. The object passes this along to the resolver framework,
         * and observers that have registered themselves for the provider are notified.
         */
        getContext().getContentResolver().notifyChange(uri, null);

        // Returns the number of rows updated.
        return count;
    }

    /**
     * A test package can call this to get a handle to the database underlying NotePadProvider,
     * so it can insert test data into the database. The test case class is responsible for
     * instantiating the provider in a test context; {@link android.test.ProviderTestCase2} does
     * this during the call to setUp()
     *
     * @return a handle to the database helper object for the provider's data.
     */
    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}
