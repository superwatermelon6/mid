/*
 * 版权所有 (C) 2007 Android 开源项目
 *
 * 根据 Apache 许可证 2.0 版（“许可证”）的条款，除非遵守许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则根据许可证分发的软件按“原样”分发，
 * 不提供任何形式的明示或暗示的保证或条件。请参阅许可证以了解具体的许可权限和限制。
 */

package com.example.android.notepad;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * 定义了记事本内容提供者与其客户端之间的契约。契约定义了客户端访问提供者所需的信息，包括一个或多个数据表。
 * 契约是一个公共的、不可扩展（final）类，包含定义列名和 URI 的常量。编写良好的客户端仅依赖于契约中的常量。
 */
public final class NotePad {
    public static final String AUTHORITY = "com.google.provider.NotePad";

    // 此类不能被实例化
    private NotePad() {
    }

    /**
     * 笔记表契约
     */
    public static final class Notes implements BaseColumns {

        // 此类不能被实例化
        private Notes() {}

        /**
         * 该提供者提供的表名
         */
        public static final String TABLE_NAME = "notes";

        /*
         * URI 定义
         */

        /**
         * 该提供者的 URI 方案部分
         */
        private static final String SCHEME = "content://";

        /**
         * URI 的路径部分
         */

        /**
         * 笔记 URI 的路径部分
         */
        private static final String PATH_NOTES = "/notes";

        /**
         * 笔记 ID URI 的路径部分
         */
        private static final String PATH_NOTE_ID = "/notes/";

        /**
         * 笔记 ID URI 路径部分中笔记 ID 段的 0 基位置
         */
        public static final int NOTE_ID_PATH_POSITION = 1;

        /**
         * 直播文件夹 URI 的路径部分
         */
        private static final String PATH_LIVE_FOLDER = "/live_folders/notes";

        /**
         * 该表的内容 URI
         */
        public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_NOTES);

        /**
         * 单个笔记的内容 URI 基础。调用者必须将数字笔记 ID 追加到此 URI 以检索笔记
         */
        public static final Uri CONTENT_ID_URI_BASE
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID);

        /**
         * 按 ID 指定的单个笔记的内容 URI 匹配模式。用于匹配传入的 URI 或构造 Intent。
         */
        public static final Uri CONTENT_ID_URI_PATTERN
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID + "/#");

        /**
         * 笔记直播文件夹的内容 URI 模式
         */
        public static final Uri LIVE_FOLDER_URI
            = Uri.parse(SCHEME + AUTHORITY + PATH_LIVE_FOLDER);

        /*
         * MIME 类型定义
         */

        /**
         * 提供笔记目录的 {@link #CONTENT_URI} 的 MIME 类型。
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.note";

        /**
         * 单个笔记的 {@link #CONTENT_URI} 子目录的 MIME 类型。
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.note";

        /**
         * 该表的默认排序顺序
         */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";

        /*
         * 列定义
         */

        /**
         * 笔记标题的列名
         * <P>类型：TEXT</P>
         */
        public static final String COLUMN_NAME_TITLE = "title";

        /**
         * 笔记内容的列名
         * <P>类型：TEXT</P>
         */
        public static final String COLUMN_NAME_NOTE = "note";

        /**
         * 创建时间戳的列名
         * <P>类型：INTEGER (long from System.currentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /**
         * 修改时间戳的列名
         * <P>类型：INTEGER (long from System.currentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";
    }
}
