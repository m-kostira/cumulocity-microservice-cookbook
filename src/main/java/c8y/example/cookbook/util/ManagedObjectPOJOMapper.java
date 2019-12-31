package c8y.example.cookbook.util;

import java.io.IOException;
import java.util.Map;

import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Provides utility methods for serialization and deserialization of Java objects in the inventory.   
 * 
 * Uses Jackson ObjectMapper under the hood to encode a POJO as a Map<String, Object>
 * under a property of a ManagedObjectRepresentation instance. 
 * 
 * Unlike the default c8y SDK serialization/deserialization (built on svenson), this approach handles polymorphic types. 
 * This means it's possible to serialize a collection of objects with a common supertype but different concrete class.
 *  
 * Polymorphic types with annotations(ManagedObjectPOJOMapper.DEFAULT):  
 * Interfaces should be annotated with @JsonTypeInfo and @JsonSubTypes. 
 * Implementing classes should be annotated with @JsonTypeName. 
 * 
 * Polymorphic types without annotations(ManagedObjectPOJOMapper.TRUSTED_SOURCE):
 * If the JSON is from a trusted source, an ObjectMapper with default typing enabled may be supplied:
 * objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
 * This makes annotations unnecessary.
 * 
 * Note: this is built to work with the c8y SDK, namely the ManagedObjectRepresentation class. As a side effect, 
 * the deserialization is not very efficient. To improve performance, the same mechanism can be adapted to on work with raw 
 * JSON instead of ManagedObjectRepresentation.  
 *   
 * 
 * @author MKOS
 */
/**
 * @author MKOS
 *
 */
public class ManagedObjectPOJOMapper {
	
	private ObjectMapper objectMapper;
	private ObjectMapper defaultObjectMapper = new ObjectMapper();
	
	/**
	 * Polymorphic types with annotations
	 *   
     * Interfaces should be annotated with @JsonTypeInfo and @JsonSubTypes.
     * Implementing classes should be annotated with @JsonTypeName. 
	 */
	public static ManagedObjectPOJOMapper DEFAULT = new ManagedObjectPOJOMapper();
	
	/**
	 * Polymorphic types without annotations,(use if the JSON is from a trusted source)
	 */
	public static ManagedObjectPOJOMapper TRUSTED_SOURCE = new ManagedObjectPOJOMapper(true);

	/**
	 * The name of the json property of the ManagedObject which will hold the serialized Java object.
     * 
	 * @param objectClass
	 * @return
	 */
	public static String getDefaultFragmentNameForClass(Class objectClass) {
		// note that if we don't prefix with '@', the svenson library used by the c8y SDK will
		// attempt to serialize/deserialize our object and will fail if we have polymorphic classes with
		// type annotation; instead, we bypass svenson and do our own 
		// (de)serialization with jackson ObjectMapper
		return '@' + objectClass.getName().replace('.', '_');
	}
	
	public ManagedObjectPOJOMapper() {
		this(false);
	}

	public ManagedObjectPOJOMapper(ObjectMapper objectMapper) {
		super();
		this.objectMapper = objectMapper;
	}
			
	/**
	 * @param enableObjectMapperDefaultTyping allows to write and read polymorphic types without annotations;
	 * security issue if deserializing untrusted JSON - use only if incoming JSON is from a trusted source;
	 */
	public ManagedObjectPOJOMapper(boolean enableObjectMapperDefaultTyping) {
		objectMapper = new ObjectMapper();
		objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		
		if (enableObjectMapperDefaultTyping) {
			//objectMapper.enableDefaultTyping -security issue if deserializing untrusted JSON
			objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
		}
	}

	/**
	 * Serializes a Java object as a property of a managed object 
	 *  
	 * @param managedObjectRepresentation The managed object representation to which to write the Java object 
	 * @param object The Java object to write
	 * @throws IOException 
	 */
	public void writePOJO(ManagedObjectRepresentation managedObjectRepresentation,	 Object object) throws IOException {
		writePOJO(managedObjectRepresentation, object, null);
	}
	
	/**
	 * Serializes a Java object as a property of a managed object
	 * 
	 * @param managedObjectRepresentation The managed object representation to which to write the Java object 
	 * @param object The Java object to write
	 * @param fragmentName The name of the json property of the managed object which will hold the serialized Java object.
	 * @throws IOException 
	 */
	public void writePOJO(ManagedObjectRepresentation managedObjectRepresentation,	 Object object, String fragmentName) throws IOException {
		if (fragmentName == null) {
			fragmentName = getDefaultFragmentNameForClass(object.getClass());
		}
		
		String jsonString = objectMapper.writeValueAsString(object);
		
		Map<String, Object> map = new ObjectMapper().readValue(jsonString, new TypeReference<Map<String, Object>>() {});
			
		//convertValue is probably faster but doesn't work if default typing is enabled
		//Map<String, Object> map = objectMapper.convertValue(object, Map.class); 
		
		managedObjectRepresentation.set(map, fragmentName);
	}
	
	public <V> V readPOJO(ManagedObjectRepresentation managedObjectRepresentation,	
			Class <V> objectClass, String fragmentName) throws JsonParseException, JsonMappingException, IOException, ClassNotFoundException {
		if (fragmentName == null) {
			fragmentName = getDefaultFragmentNameForClass(objectClass);
		}
		
		Map<String, Object> map =  (Map<String, Object>) managedObjectRepresentation
				.get(fragmentName);
		
		// convertValue does not work with default typing, that's why we do 
		// intermediate conversion to string below instead
		//V object = objectMapper.convertValue(map, objectClass);
				
		// use the defaultObjectMapper which has disabled defaultTyping  
		String jsonString = defaultObjectMapper.writeValueAsString(map);
		
		String typeInfo = (String) map.get("@class");
		V object = (V) objectMapper.readValue(jsonString, Class.forName(typeInfo));
		
		
		return object;
	}

	public <V> V readPOJO(ManagedObjectRepresentation managedObjectRepresentation,
			Class<V> objectClass) throws JsonParseException, JsonMappingException, IOException, ClassNotFoundException {
		return readPOJO(managedObjectRepresentation, objectClass, null);
	}
		
	/**
	 * Deserialize a managed object which is in the form of a key-value map, (e.g. the output of
	 * a json parsing library such as restassured) 
	 * 
	 * @param managedObject The managed object as a key-value map
	 */
	public <V> V readPOJO(Map<Object,Object> managedObject,
			Class<V> objectClass, String fragmentName) throws JsonParseException, JsonMappingException, IOException, ClassNotFoundException {
		if (fragmentName == null) {
			fragmentName = getDefaultFragmentNameForClass(objectClass);
		}
		
		@SuppressWarnings("unchecked")
		Map<String, Object> map =  (Map<String, Object>) managedObject
				.get(fragmentName);
		
		// convertValue does not work with default typing, that's why we do 
		// intermediate conversion to string below instead
		//V object = objectMapper.convertValue(map, objectClass);
				
		// use the defaultObjectMapper which has disabled defaultTyping  
		String jsonString = defaultObjectMapper.writeValueAsString(map);
		
		String typeInfo = (String) map.get("@class");
		V object = (V) objectMapper.readValue(jsonString, Class.forName(typeInfo));
				
		return object;		
	}

	/**
	 * Deserialize a managed object which is in the form of a key-value map, (e.g. the output of
	 * a json parsing library such as restassured) 
	 * @param managedObject The managed object as a key-value map
	 */
	public <V> V readPOJO(Map<Object,Object> managedObject,
			Class<V> objectClass) throws JsonParseException, JsonMappingException, IOException, ClassNotFoundException {
		return readPOJO(managedObject, objectClass, null);
	}

	
}
