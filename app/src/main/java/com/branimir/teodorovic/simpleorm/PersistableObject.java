package com.branimir.teodorovic.simpleorm;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public abstract class PersistableObject {
    @DatabaseField protected long id;

    private static Context mContext;
    private static DbHelper dbHelper;

    public long getId() {
        return id;
    }

    private void setId(long id) {
        this.id = id;
    }

    public long save(){
        if(dbHelper == null)
            setDatabase(mContext, this.getClass());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        try {
            for(Field field: this.getClass().getDeclaredFields()){
                if(field.isAnnotationPresent(DatabaseField.class)) {
                    field.setAccessible(true);

                    if(field.getType().isPrimitive()) {
                        switch (field.getType().getName()) {
                            case "char":
                                cv.put(field.getName(), String.valueOf(field.get(this)));
                                break;
                            case "byte":
                                cv.put(field.getName(), (byte) field.get(this));
                                break;
                            case "short":
                                cv.put(field.getName(), (short) field.get(this));
                            case "int":
                                cv.put(field.getName(), (int) field.get(this));
                                break;
                            case "long":
                                cv.put(field.getName(), (long) field.get(this));
                                break;
                            case "float":
                                cv.put(field.getName(), (float) field.get(this));
                                break;
                            case "double":
                                cv.put(field.getName(), (double) field.get(this));
                                break;
                            case "boolean":
                                cv.put(field.getName(), (boolean) field.get(this));
                                break;
                            case "byte[]":
                                cv.put(field.getName(), (byte[]) field.get(this));
                            default:
                                throw new Exception("unsupported type");
                        }

                    } else if (field.getType() == String.class) {
                        cv.put(field.getName(), (String) field.get(this));
                    } else if(field.getType() == Date.class){
                        long time = ((Date) field.get(this)).getTime();
                        cv.put(field.getName(), time);
                    } else if(PersistableObject.class.isAssignableFrom(field.getType())){
                        // ako je custom objekat, provjeriti da li se vec nalazi u bezi, ako ne ubaciti ga
                        PersistableObject object = (PersistableObject) field.get(this);
                        if (object != null)
                            cv.put(field.getName(), object.save());
                        else {
                            cv.put(field.getName(), 0);
                        }
                    } else {
                        throw new Exception("Invalid type. You can only save primitive types, byte[], String, Date and instances of PersistableObject subclass");
                    }
                }
            }

        } catch (Exception e) {
            Log.e(PersistableObject.class.getName(), e.getMessage());
        }

        long returnedId = 0;

        if(id == 0){
            returnedId = db.insert(this.getClass().getSimpleName(), null, cv);
        } else {
            db.update(this.getClass().getSimpleName(), cv, "id = ?", new String[]{String.valueOf(id)});
        }
        setId(returnedId);
        return returnedId;
    }

    public void delete(){
        if(dbHelper == null)
            setDatabase(mContext, this.getClass());

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        if(db.delete(this.getClass().getSimpleName(), "id = ?", new String[]{String.valueOf(id)}) > 0)
            id = 0;
    }

    private static <T> T getOne(Class<T> type, Cursor cursor){
        T object = null;
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Field[] classFields = type.getDeclaredFields();
        Field[] inheritedFields = type.getSuperclass().getDeclaredFields();
        Field[] fields = new Field[classFields.length + inheritedFields.length];
        for(int i=0; i<classFields.length; i++){
            fields[i] = classFields[i];
        }
        for(int i=classFields.length; i<classFields.length + inheritedFields.length; i++){
            fields[i] = inheritedFields[i - classFields.length];
        }

        try {
            object = type.newInstance();
            for(Field field: fields){
                if(field.isAnnotationPresent(DatabaseField.class)) {
                    field.setAccessible(true);

                    if(field.getType().isPrimitive()) {
                        switch (field.getType().getName()) {
                            case "char":
                                field.setChar(object, cursor.getString(cursor.getColumnIndex(field.getName())).toCharArray()[0]);
                            case "byte":
                                field.setByte(object, (byte) cursor.getInt(cursor.getColumnIndex(field.getName())));
                                break;
                            case "short":
                                field.setShort(object, cursor.getShort(cursor.getColumnIndex(field.getName())));
                            case "int":
                                field.setInt(object, cursor.getInt(cursor.getColumnIndex(field.getName())));
                                break;
                            case "long":
                                field.setLong(object, cursor.getLong(cursor.getColumnIndex(field.getName())));
                                break;
                            case "float":
                                field.setFloat(object, cursor.getFloat(cursor.getColumnIndex(field.getName())));
                                break;
                            case "double":
                                field.setDouble(object, cursor.getDouble(cursor.getColumnIndex(field.getName())));
                                break;
                            case "boolean":
                                boolean booleanValue = cursor.getInt(cursor.getColumnIndex(field.getName())) == 0 ? false : true;
                                field.setBoolean(object, booleanValue);
                                break;
                            case "byte[]":
                                byte[] bytes = cursor.getBlob(cursor.getColumnIndex(field.getName()));
                                field.set(object, bytes);
                            default:
                                throw new Exception("unsupported type");
                        }

                    } else if (field.getType() == String.class) {
                        field.set(object, cursor.getString(cursor.getColumnIndex(field.getName())));
                    } else if (field.getType() == Date.class) {
                        Date date = new Date(cursor.getInt(cursor.getColumnIndex(field.getName())));
                        field.set(object, date);
                    } else if (PersistableObject.class.isAssignableFrom(field.getType())){
                        // izvaditi taj jedan i objectu dodati njegov id
                        long id = cursor.getLong(cursor.getColumnIndex(field.getName()));
                        Cursor c = db.query(field.getType().getSimpleName(), null, "id = ?", new String[]{String.valueOf(id)}, null, null, null);
                        if (c.moveToFirst())
                            field.set(object, getOne(field.getType(), c));
                    }
                }
            }
        } catch (Exception e){
            Log.e(PersistableObject.class.getName(), e.getMessage());
        }

        return object;
    }

    public static <T> List<T> get(Class<T> type, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit){
        if(dbHelper == null)
            setDatabase(mContext, type);

        List<T> result = new ArrayList<T>();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(type.getSimpleName(), null, selection, selectionArgs, groupBy, having, orderBy, limit);

        try {
            while (cursor.moveToNext()){
                T object = getOne(type, cursor);
                result.add(object);
            }
        } catch (Exception e) {
            Log.e(PersistableObject.class.getName(), e.getMessage());
        }

        return result;
    }

    public static <T> List<T> getAll(Class<T> type){
        return get(type, null, null, null, null, null, null);
    }

    public static <T> void save(Class<T> type, List<T> list){
        if(dbHelper == null)
            setDatabase(mContext, type.getClass());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        db.beginTransaction();

        for(Object object: list){
            T t = type.cast(object);
            ((PersistableObject) t).save();
        }

        db.endTransaction();
    }

    public static <T> int delete(Class<T> type, String where, String[] whereArgs){
        if(dbHelper == null)
            setDatabase(mContext, type.getClass());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        return db.delete(type.getSimpleName(), where, whereArgs);
    }

    private static void setDatabase(Context context, Class object){
        String createSql = null;

        try {
            Field[] fieldsArray = object.getDeclaredFields();

            List<Field> fields = new ArrayList<Field>(fieldsArray.length);
            for(Field field: fieldsArray){
                fields.add(field);
            }

            Collections.sort(fields, new Comparator<Field>() {
                @Override
                public int compare(Field field1, Field field2) {
                    return field1.getName().compareTo(field2.getName());
                }
            });

            StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS " + object.getSimpleName() +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT");

            // get name and type of all fields
            for (Field field : fields) {
                if(field.isAnnotationPresent(DatabaseField.class)){
                    field.setAccessible(true);

                    String type = "";
                    if(field.getType().isPrimitive()) {
                        switch (field.getType().getName()) {
                            case "char":
                                type = "TEXT";
                                break;
                            case "byte":
                                type = "INTEGER";
                                break;
                            case "short":
                                type = "INTEGER";
                                break;
                            case "int":
                                type = "INTEGER";
                                break;
                            case "long":
                                type = "INTEGER";
                                break;
                            case "float":
                                type = "REAL";
                                break;
                            case "double":
                                type = "REAL";
                                break;
                            case "boolean":
                                type = "INTEGER";
                                break;
                            case "byte[]":
                                type = "BLOB";
                                break;
                            default:
                                throw new Exception("unsupported type");
                        }

                    } else if (field.getType() == String.class){
                        type = "TEXT";
                    } else if(field.getType() == Date.class) {
                        type = "INTEGER";
                    } else if (PersistableObject.class.isAssignableFrom(field.getType())) {
                        setDatabase(mContext, field.getType());
                        type = "INTEGER";
                    } else {
                        throw new Exception("Invalid type. You can only use primitive types, byte[], String, Date and instances of PersistableObject subclass");
                    }

                    sb.append(", " + field.getName() + " " + type);
                    if(field.isAnnotationPresent(Unique.class))
                        sb.append(" UNIQUE ON CONFLICT REPLACE");
                }
            }

            sb.append(");");
            createSql = sb.toString();
        } catch (Exception e){
            Log.e(PersistableObject.class.getName(), e.getMessage());
            return;
        }

        if(createSql != null) {
            String[] packageArray = context.getPackageName().split("\\.");
            String name = packageArray[packageArray.length-1] + ".db";

            String PREF_SQL = object.getClass().getPackage().getName() + "_" + object.getSimpleName() + "_createSQL";
            String PREF_VERSION = object.getClass().getPackage().getName() + "_version";

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            String savedSql = prefs.getString(PREF_SQL, null);

            int version = prefs.getInt(PREF_VERSION, -1);
            if(version == -1) {
                version = 1;
                prefs.edit().putString(PREF_SQL, createSql).apply();
                prefs.edit().putInt(PREF_VERSION, version).apply();
            }
            else if(savedSql == null || !createSql.equals(savedSql)){
                prefs.edit().putString(PREF_SQL, createSql).apply();
                prefs.edit().putInt(PREF_VERSION, ++version).apply();
            }

            dbHelper = new DbHelper(mContext, name, object.getSimpleName(), createSql, version);
            if(dbHelper.getWritableDatabase() == null)
                dbHelper.onCreate(dbHelper.getWritableDatabase());
        }
    }

    public static <T> Cursor getCursor(Class<T> type, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit){
        if(dbHelper == null)
            setDatabase(mContext, type);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(type.getSimpleName(), columns, selection, selectionArgs, groupBy, having, orderBy, limit);
        return cursor;
    }

    public static <T> long getCount(Class<T> type){
        if(dbHelper == null)
            setDatabase(mContext, type);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return DatabaseUtils.queryNumEntries(db, type.getSimpleName());
    }

    public static void setContext(Context context) {
        PersistableObject.mContext = context;
    }

    private static class DbHelper extends SQLiteOpenHelper {
        private String createSQL;
        private String tableName;


        public DbHelper(Context context, String name, String tableName, String createSQL, int version) {
            super(context, name, null, version);
            this.createSQL = createSQL;
            this.tableName = tableName;
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            sqLiteDatabase.execSQL(createSQL);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + tableName + ";");
            onCreate(sqLiteDatabase);
        }
    }
}
