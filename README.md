Object-relational mapping library for Android

To save instance of your class, extend the PersistableObject

	public class Book extends PersistableObject {
	
	@DatabaseField private String name;
	@DatabaseField private Author author;

	public Book() {}

	public Book(String name, Author author) {
		this.name = name;
		this.author = author;
		}

	// getters and setters
	}
For every field you want to save add @DatabaseField annotation

You can save primitive type, String, byte[], Date and and instances of PersistentObject subclass
	
	public class Book extends PersistableObject {

	@DatabaseField private String name;
	@DatabaseField private Author author;

	public Book() {}

	public Book(String name, Author author) {
		this.name = name;
		this.author = author;
	} ...

Use annotation @Unique to mark field that will be unque in the database. In case of conflict, object will be automatically replaced with the new value

In your application onCreate() or activity onCreate() add PersistableObject.setContext(this);

To save and read objects:

	Author author = new Author("William", " Shakespeare");
	Book book = new Book("Romeo and Juliet", author);

	Book book2 = new Book("Book without author", null);

	book.save();
	book2.save();

	ArrayList<Book> books = PersistableObject.getAll(Book.class);

author will also be saved.

Use book.delete() to delete if from the database

When retreiveing objects you can also use PersistableObject.get(Class type, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) to perform selection

Use PersistableObject.getCursor(Class type, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) to retreive custom cursor

Use PersistableObject.save(Class type, List list) to save list of Objects
